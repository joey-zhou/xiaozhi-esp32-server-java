<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xl="6" :lg="12" :xs="24" v-for="item in queryFilter" :key="item.index">
                <a-form-item :label="item.label">
                  <a-input-search v-model="query[item.index]" placeholder="请输入" allow-clear @search="getData()" />
                </a-form-item>
              </a-col>
              <a-col :xxl="6" :xl="6" :lg="12" :xs="24">
                <a-form-item label="状态">
                  <a-select v-model="query.state" @change="getData()">
                    <a-select-option v-for="item in stateItems" :key="item.value">
                      <span>{{ item.label }}</span>
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        <!-- 表格数据 -->
        <a-card title="查询表格" :bodyStyle="{ padding: 0 }" :bordered="false">
          <a-input-search slot="extra" enter-button="添加设备" autoFocus placeholder="请输入设备码" @search="addDevice" />
          <a-table rowKey="deviceId" :columns="tableColumns" :data-source="data" :loading="loading"
            :pagination="pagination" :scroll="{ x: 1200 }" size="middle">
            <a-button slot="footer" :loading="exportLoading" :disabled="true" @click="">
              导出
            </a-button>
            <!-- deviceName部分保持不变 -->
            <template v-for="col in ['deviceName']" :slot="col" slot-scope="text, record">
              <div :key="col">
                <a-input v-if="record.editable" style="margin: -5px 0; text-align: center" :value="text" @change="
                  (e) => inputEdit(e.target.value, record.deviceId, col)
                " @keyup.enter="(e) => update(record, record.deviceId)" @keyup.esc="(e) => cancel(record.deviceId)" />
                <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                  <a-tooltip title="点击编辑" :mouseEnterDelay="0.5">
                    <span v-if="text">{{ text }}</span>
                    <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                  </a-tooltip>
                </span>
                <span v-else>{{ text }}</span>
              </div>
            </template>

            <!-- roleName 下拉框 -->
            <template slot="roleName" slot-scope="text, record">
              <a-select v-if="record.editable" style="margin: -5px 0; text-align: center; width: 100%"
                :value="record.roleId" @change="
                  (value) => handleSelectChange(value, record.deviceId, 'role')
                ">
                <a-select-option v-for="item in roleItems" :key="item.roleId" :value="item.roleId">
                  <div style="text-align: center">{{ item.roleName }}</div>
                </a-select-option>
              </a-select>
              <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                <a-tooltip :title="record.roleDesc" :mouseEnterDelay="1" placement="right">
                  <span v-if="text">{{ text }}</span>
                  <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                </a-tooltip>
              </span>
              <span v-else>{{ text }}</span>
            </template>

            <!-- modelName 下拉框 -->
            <template slot="modelName" slot-scope="text, record">
              <a-cascader v-if="record.editable" style="margin: -5px 0; text-align: center; width: 100%"
                :options="modelOptions" :value="getCascaderValue(record)"
                @change="(value) => handleModelChange(value, record.deviceId)" placeholder="请选择模型"
                expandTrigger="hover" />
              <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                <a-tooltip :title="record.modelDesc || ''" :mouseEnterDelay="0.5">
                  <span v-if="record.modelId && record.modelName">
                    {{ record.modelName }}
                    <a-tag v-if="record.modelType === 'agent'" color="blue" size="small">智能体</a-tag>
                  </span>
                  <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                </a-tooltip>
              </span>
              <span v-else>
                {{ record.modelName || '' }}
                <a-tag v-if="record.modelType === 'agent'" color="blue" size="small">智能体</a-tag>
              </span>
            </template>

            <!-- sttName 下拉框 -->
            <template slot="sttName" slot-scope="text, record">
              <a-select v-if="record.editable" style="margin: -5px 0; text-align: center; width: 100%"
                :value="record.sttId" @change="
                  (value) => handleSelectChange(value, record.deviceId, 'stt')
                ">
                <a-select-option v-for="item in sttItems" :key="item.sttId" :value="item.sttId">
                  <div style="text-align: center">{{ item.sttName }}</div>
                </a-select-option>
              </a-select>
              <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                <a-tooltip :title="record.sttDesc" :mouseEnterDelay="0.5">
                  <span v-if="record.sttId">{{
                    getItemName(sttItems, "sttId", record.sttId, "sttName")
                  }}</span>
                  <span v-else style="padding: 0 50px">Vosk本地识别</span>
                </a-tooltip>
              </span>
              <span v-else>{{
                getItemName(sttItems, "sttId", record.sttId, "sttName")
              }}</span>
            </template>

            <!-- 其他模板保持不变 -->
            <template slot="state" slot-scope="text">
              <a-tag color="green" v-if="text == 1">在线</a-tag>
              <a-tag color="red" v-else>离线</a-tag>
            </template>

            <template slot="operation" slot-scope="text, record">
              <a-space v-if="record.editable">
                <a-popconfirm href="javascript:;" title="确定保存？" @confirm="update(record, record.deviceId)">
                  <a>保存</a>
                </a-popconfirm>
                <a href="javascript:;" @click="cancel(record.deviceId)">取消</a>
              </a-space>
              <a-space v-else>
                <a href="javascript:" @click="edit(record.deviceId)">编辑</a>
                <a href="javascript:" @click="editWithDialog(record)">详情</a>
                <a-popconfirm
                  title="确定要删除此设备吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="deleteDevice(record)"
                >
                  <a href="javascript:" style="color: #ff4d4f">删除</a>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table>
        </a-card>
      </div>
    </a-layout-content>
    <a-back-top />

    <DeviceEditDialog @submit="update" @close="editVisible = false" :visible="editVisible" :current="currentDevice"
      :model-items="modelItems" :stt-items="sttItems" :role-items="roleItems"></DeviceEditDialog>
  </a-layout>
</template>

<script>
import axios from "@/services/axios";
import api from "@/services/api";
import mixin from "@/mixins/index";
import { message } from "ant-design-vue";
import DeviceEditDialog from "@/components/DeviceEditDialog.vue";
export default {
  components: { DeviceEditDialog },
  mixins: [mixin],
  data() {
    return {
      // 查询框
      editVisible: false,
      currentDevice: {},
      query: {
        state: "",
      },
      queryFilter: [
        {
          label: "设备编号",
          value: "",
          index: "deviceId",
        },
        {
          label: "设备名称",
          value: "",
          index: "deviceName",
        },
      ],
      stateItems: [
        {
          label: "全部",
          value: "",
          key: "",
        },
        {
          label: "在线",
          value: "1",
          key: "1",
        },
        {
          label: "离线",
          value: "0",
          key: "0",
        },
      ],
      // 表格数据
      tableColumns: [
        {
          title: "设备编号",
          dataIndex: "deviceId",
          scopedSlots: { customRender: "deviceId" },
          width: 160,
          fixed: "left",
          align: "center",
        },
        {
          title: "设备名称",
          dataIndex: "deviceName",
          scopedSlots: { customRender: "deviceName" },
          width: 100,
          align: "center",
        },
        {
          title: "设备角色",
          dataIndex: "roleName",
          scopedSlots: { customRender: "roleName" },
          width: 100,
          align: "center",
        },
        {
          title: "模型",
          dataIndex: "modelName",
          scopedSlots: { customRender: "modelName" },
          width: 150,
          align: "center",
        },
        {
          title: "语音识别",
          dataIndex: "sttName",
          scopedSlots: { customRender: "sttName" },
          width: 150,
          align: "center",
        },
        {
          title: "WIFI名称",
          dataIndex: "wifiName",
          scopedSlots: { customRender: "wifiName" },
          width: 100,
          align: "center",
          ellipsis: true,
        },
        {
          title: "IP地址",
          dataIndex: "ip",
          scopedSlots: { customRender: "ip" },
          width: 180,
          align: "center",
          ellipsis: true,
        },
        {
          title: "设备状态",
          dataIndex: "state",
          scopedSlots: { customRender: "state" },
          width: 100,
          align: "center",
        },
        {
          title: "产品类型",
          dataIndex: "chipModelName",
          width: 100,
          align: "center",
        },
        {
          title: "版本号",
          dataIndex: "version",
          width: 100,
          align: "center",
        },
        {
          title: "活跃时间",
          dataIndex: "lastLogin",
          scopedSlots: { customRender: "lastLogin" },
          width: 180,
          align: "center",
        },
        {
          title: "创建时间",
          dataIndex: "createTime",
          scopedSlots: { customRender: "createTime" },
          width: 180,
          align: "center",
        },
        {
          title: "操作",
          dataIndex: "operation",
          scopedSlots: { customRender: "operation" },
          width: 150,
          align: "center",
          fixed: "right",
        },
      ],
      roleItems: [],
      modelItems: [],
      agentItems: [],
      modelOptions: [
        {
          value: "llm",
          label: "LLM模型",
          children: [],
        },
        {
          value: "agent",
          label: "智能体",
          children: [],
        },
      ],
      ttsItems: [],
      sttItems: [],
      data: [],
      cacheData: [],
      // 操作单元格是否可编辑
      editingKey: "",
      // 审核链接
      verifyCode: "",
      // 加载状态标志
      configLoaded: false,
      agentsLoaded: false,
    };
  },
  mounted() {
    // 并行加载配置和智能体数据
    this.getRole();

    Promise.all([
      this.getConfig(),
      this.getAgents()
    ]).then(() => {
      // 两者都加载完成后，获取设备数据
      this.getData();
    });
  },
  methods: {
    /* 查询参数列表 */
    getData() {
      this.loading = true;
      this.editingKey = "";
      axios
        .get({
          url: api.device.query,
          data: {
            start: this.pagination.page,
            limit: this.pagination.pageSize,
            ...this.query,
          },
        })
        .then((res) => {
          if (res.code === 200) {
            // 先保存数据
            const deviceList = res.data.list.map((item) => {
              item.sttId = item.sttId || -1;
              return item;
            });

            // 无论模型和智能体数据是否加载完成，都尝试处理设备的模型类型
            // 移除条件判断，确保始终执行
            deviceList.forEach(device => {
              this.determineModelType(device);
            });

            this.data = deviceList;
            this.cacheData = deviceList.map((item) => ({ ...item }));
            this.pagination.total = res.data.total;
          } else {
            this.$message.error(res.message);
          }
        })
        .catch((e) => {
          this.$message.error("服务器维护/重启中，请稍后再试");
        })
        .finally(() => {
          this.loading = false;
        });
    },

    // 确定模型类型（LLM或智能体）
    determineModelType(device) {
      if (!device.modelId) {
        // 确保没有modelId的设备也有基本属性
        device.modelType = '';
        device.modelName = '';
        device.modelDesc = '';
        return;
      }

      // 转换为数字进行比较（确保类型一致）
      const modelId = Number(device.modelId);

      // 检查是否为智能体
      const agent = this.agentItems.find(a => Number(a.configId) === modelId);

      if (agent) {
        // 是智能体
        device.modelType = 'agent';
        device.modelName = agent.agentName || '未知智能体';
        device.modelDesc = agent.agentDesc || '';
        device.provider = 'coze'; // 标记提供商
      } else {
        // 检查是否为LLM模型
        const model = this.modelItems.find(m => Number(m.configId) === modelId);

        if (model) {
          // 是LLM模型
          device.modelType = 'llm';
          device.modelName = model.configName || '未知模型';
          device.modelDesc = model.configDesc || '';
          device.provider = model.provider || '';
        } else {
          // 未找到匹配的模型，但仍然设置基本信息
          console.warn(`未找到ID为${modelId}的模型或智能体`);
          device.modelType = 'unknown';
          device.modelName = `未知模型(ID:${modelId})`;
          device.modelDesc = '';
        }
      }
    },

    // 获取级联选择器的值
    getCascaderValue(record) {
      if (!record.modelId) return [];

      // 转换为数字类型，确保类型一致性
      const modelId = Number(record.modelId);

      // 使用modelType字段确定类型
      if (record.modelType === 'agent') {
        return ["agent", modelId];
      } else if (record.modelType === 'llm') {
        return ["llm", modelId];
      } else {
        // 默认情况下，尝试在两个列表中查找
        const isAgent = this.agentItems.some(a => Number(a.configId) === modelId);
        return isAgent ? ["agent", modelId] : ["llm", modelId];
      }
    },

    // 添加设备
    addDevice(value, event) {
      if (value === "") {
        this.$message.info("请输入设备编号");
        return;
      }
      axios
        .post({
          url: api.device.add,
          data: {
            code: value,
          },
        })
        .then((res) => {
          if (res.code === 200) {
            this.getData();
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.$message.error("服务器维护/重启中，请稍后再试");
        });
    },
    // 添加删除设备方法
    deleteDevice(record) {
      this.loading = true;
      axios
        .post({
          url: api.device.delete,
          data: {
            deviceId: record.deviceId
          }
        })
        .then((res) => {
          if (res.code === 200) {
            this.$message.success("设备删除成功");
            this.getData();
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.$message.error("服务器维护/重启中，请稍后再试");
        })
        .finally(() => {
          this.loading = false;
        });
    },
    
    // 选择变更处理函数
    handleSelectChange(value, key, type) {
      // 根据类型确定要使用的数据源和字段名
      let items, idField, nameField;

      if (type === "role") {
        items = this.roleItems;
        idField = "roleId";
        nameField = "roleName";
      } else if (type === "model") {
        items = this.modelItems;
        idField = "modelId";
        nameField = "modelName";
      } else if (type === "stt") {
        items = this.sttItems;
        idField = "sttId";
        nameField = "sttName";
      } else {
        return; // 不支持的类型，直接返回
      }

      // 查找对应的项
      const item = items.find((item) => item[idField] === value);
      const name = item ? item[nameField] : "";

      // 更新数据
      const data = this.editLine(key);
      data.target[idField] = value;
      data.target[nameField] = name;
      this.data = [...this.data]; // 强制更新视图
    },
    // 更新设备消息
    update(val, key) {
      if (key) {
        this.loading = true;
        delete val.editable;
      }

      axios
        .post({
          url: api.device.update,
          data: {
            deviceId: val.deviceId,
            deviceName: val.deviceName,
            modelId: val.modelId,
            sttId: val.sttId,
            ttsId: val.ttsId,
            roleId: val.roleId,
          }
        })
        .then((res) => {
          if (res.code === 200) {
            this.getData();
            this.editVisible = false;
            message.success("修改成功");
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          message.error("服务器维护/重启中,请稍后再试");
        })
        .finally(() => {
          this.loading = false;
        });
    },
    // 获取角色列表
    getRole() {
      axios
        .get({
          url: api.role.query,
          data: {},
        })
        .then((res) => {
          if (res.code === 200) {
            this.roleItems = res.data.list;
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.$message.error("服务器维护/重启中，请稍后再试");
        });
    },
    // 获取模型列表
    getConfig() {
      return new Promise((resolve) => {
        axios
          .get({
            url: api.config.query,
            data: {},
          })
          .then((res) => {
            if (res.code === 200) {
              this.sttItems.push({
                sttId: -1,
                sttName: "Vosk本地识别",
                sttDesc: "默认Vosk本地语音识别模型",
              });

              // 清空现有LLM模型子项
              this.modelOptions[0].children = [];

              res.data.list.forEach((item) => {
                if (item.configType == "llm") {
                  // 确保configId是数字类型
                  item.configId = item.configId;
                  item.modelId = item.configId;
                  item.modelName = item.configName;
                  item.modelDesc = item.configDesc;
                  this.modelItems.push(item);

                  // 添加到级联选择器的LLM选项
                  this.modelOptions[0].children.push({
                    value: item.configId,
                    label: item.configName,
                    isLeaf: true,
                    data: item,
                  });
                } else if (item.configType == "stt") {
                  item.sttId = item.configId;
                  item.sttName = item.configName;
                  item.sttDesc = item.configDesc;
                  this.sttItems.push(item);
                }
              });

              this.configLoaded = true;
              resolve();
            } else {
              this.$message.error(res.message);
              resolve();
            }
          })
          .catch(() => {
            this.$message.error("服务器维护/重启中，请稍后再试");
            resolve();
          });
      });
    },

    // 获取智能体列表
    getAgents() {
      return new Promise((resolve) => {
        axios
          .get({
            url: api.agent.query,
            data: {
              provider: "coze",
            },
          })
          .then((res) => {
            if (res.code === 200) {
              // 清空现有智能体子项
              this.modelOptions[1].children = [];

              // 添加智能体到级联选择器
              res.data.list.forEach((item) => {
                // 确保configId是数字类型
                item.configId = item.configId;
                // 保存configId作为modelId
                item.modelId = item.configId;
                this.agentItems.push(item);
                this.modelOptions[1].children.push({
                  value: item.configId,
                  label: item.agentName,
                  isLeaf: true,
                  data: item,
                });
              });

              this.agentsLoaded = true;
              resolve();
            } else {
              this.$message.error(res.message);
              resolve();
            }
          })
          .catch(() => {
            this.$message.error("服务器维护/重启中，请稍后再试");
            resolve();
          });
      });
    },

    // 处理级联选择器变更
    handleModelChange(value, deviceId) {
      if (!value || value.length < 2) return;
      const modelType = value[0]; // llm 或 agent
      const modelId = Number(value[1]); // 确保modelId是数字类型

      const data = this.editLine(deviceId);
      data.target.modelId = modelId; // 保存modelId，这是传给后端的值
      data.target.modelType = modelType; // 保存模型类型，仅前端使用

      // 根据类型设置显示名称和描述
      if (modelType === "llm") {
        const model = this.modelItems.find(
          (item) => Number(item.configId) === modelId
        );
        if (model) {
          data.target.modelName = model.configName;
          data.target.modelDesc = model.configDesc;
          data.target.provider = model.provider || '';
        } else {
          // 找不到对应模型时设置默认值
          data.target.modelName = `未知模型(ID:${modelId})`;
          data.target.modelDesc = '';
        }
      } else if (modelType === "agent") {
        // 从智能体列表中找到对应的智能体
        const agent = this.agentItems.find(
          (item) => Number(item.configId) === modelId
        );
        if (agent) {
          data.target.modelName = agent.agentName;
          data.target.modelDesc = agent.agentDesc;
          data.target.provider = 'coze';
        } else {
          // 找不到对应智能体时设置默认值
          data.target.modelName = `未知智能体(ID:${modelId})`;
          data.target.modelDesc = '';
        }
      }

      this.data = [...this.data]; // 强制更新视图
    },

    // 获取级联选择器的值
    getCascaderValue(record) {
      if (!record.modelId) return [];

      // 使用modelType字段确定类型
      if (record.modelType === 'agent') {
        return ["agent", record.modelId];
      } else {
        return ["llm", record.modelId];
      }
    },

    // 获取项目名称的辅助方法
    getItemName(items, idField, id, nameField) {
      const item = items.find((item) => item[idField] === id);
      return item ? item[nameField] : "";
    },

    editWithDialog(device) {
      this.editVisible = true;
      this.currentDevice = device;
    },
  },
};
</script>
<style scoped>
/* 使下拉框选项居中 */

/* 确保下拉框中的文本居中 */
>>>.ant-select-selection__rendered .ant-select-selection-selected-value {
  text-align: center !important;
  width: 100% !important;
}

/* 查询框中的下拉框保持默认对齐方式 */
>>>.table-search .ant-select-selection-selected-value {
  text-align: left !important;
}
</style>