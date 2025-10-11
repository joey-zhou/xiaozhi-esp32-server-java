<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xl="6" :lg="12" :xs="24" v-for="(item, index) in queryFilter" :key="index">
                <a-form-item :label="item.label">
                  <a-input-search v-model="item.value" :placeholder="`请输入${item.label}`" allowClear @search="pagination.page = 1; getData()" />
                </a-form-item>
              </a-col>
              <a-col :xxl="6" :xl="6" :lg="12" :xs="24">
                <a-form-item label="设备状态">
                  <a-select v-model="query.state" placeholder="请选择" @change="pagination.page = 1; getData()">
                    <a-select-option v-for="item in stateItems" :key="item.key" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        
        <!-- 表格数据 -->
        <a-card :bodyStyle="{ padding: 0 }" :bordered="false">
          <div slot="extra" style="display: flex; align-items: center;">
            <a-input-search enter-button="添加设备" autoFocus placeholder="请输入设备码" @search="addDevice" />
          </div>
          
          <template slot="title">
            <span>设备管理</span>
          </template>
          
          <a-table 
            rowKey="deviceId" 
            :columns="tableColumns" 
            :data-source="data" 
            :loading="loading"
            :pagination="pagination" 
            :scroll="{ x: 1200 }" 
            size="middle">
            
            <!-- 设备名称列 -->
            <template slot="deviceName" slot-scope="text, record">
              <div>
                <a-input v-if="record.editable" style="margin: -5px 0; text-align: center" :value="text" 
                  @change="e => inputEdit(e.target.value, record.deviceId, 'deviceName')" 
                  @keyup.enter="e => update(record, record.deviceId)" 
                  @keyup.esc="e => cancel(record.deviceId)" />
                <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                  <a-tooltip title="点击编辑" :mouseEnterDelay="0.5">
                    <span v-if="text">{{ text }}</span>
                    <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                  </a-tooltip>
                </span>
                <span v-else>{{ text }}</span>
              </div>
            </template>

            <!-- 角色列 -->
            <template slot="roleName" slot-scope="text, record">
              <a-select v-if="record.editable" style="margin: -5px 0; text-align: center; width: 100%"
                :value="record.roleId" @change="value => handleSelectChange(value, record.deviceId, 'role')">
                <a-select-option v-for="item in roleItems" :key="item.roleId" :value="item.roleId">
                  <div style="text-align: center">{{ item.roleName }}</div>
                </a-select-option>
              </a-select>
              <span v-else-if="editingKey === ''" @click="edit(record.deviceId)" style="cursor: pointer">
                <a-tooltip :title="record.roleDesc" :mouseEnterDelay="0.5" placement="right">
                  <span v-if="record.roleId">{{ getRoleName(record.roleId) }}</span>
                  <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                </a-tooltip>
              </span>
              <span v-else>{{ text }}</span>
            </template>

            <!-- 设备状态列 -->
            <template slot="state" slot-scope="text">
              <a-tag :color="text == 1 ? 'green' : 'red'">{{ text == 1 ? '在线' : '离线' }}</a-tag>
            </template>

            <!-- 时间列通用模板 -->
            <template slot="timeColumn" slot-scope="text">
              {{ text || '-' }}
            </template>

            <!-- 操作列 -->
            <template slot="operation" slot-scope="text, record">
              <a-space v-if="record.editable">
                <a-popconfirm title="确定保存？" @confirm="update(record, record.deviceId)">
                  <a>保存</a>
                </a-popconfirm>
                <a @click="cancel(record.deviceId)">取消</a>
              </a-space>
              <a-space v-else>
                <a @click="edit(record.deviceId)">编辑</a>
                <a @click="editWithDialog(record)">详情</a>
                <a-popconfirm
                  title="确定要删除此设备吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="deleteDevice(record)"
                >
                  <a style="color: #ff4d4f">删除</a>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table>
        </a-card>
      </div>
    </a-layout-content>
    
    <!-- 设备详情弹窗 -->
    <DeviceEditDialog 
      @submit="update" 
      @close="editVisible = false" 
      @clear-memory="clearMemory"
      :visible="editVisible" 
      :current="currentDevice"
      :role-items="roleItems"
      :clearMemoryLoading="clearMemoryLoading"/>

    <a-back-top />
  </a-layout>
</template>
<script>
import axios from "@/services/axios";
import api from "@/services/api";
import mixin from "@/mixins/index";
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
        {
          label: "角色",
          value: "",
          index: "roleName",
        },
      ],
      stateItems: [
        { label: "全部", value: "", key: "" },
        { label: "在线", value: "1", key: "1" },
        { label: "离线", value: "0", key: "0" },
      ],
      
      // 表格数据
      tableColumns: [
        {
          title: "设备编号",
          dataIndex: "deviceId",
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
          title: "WIFI名称",
          dataIndex: "wifiName",
          width: 100,
          align: "center"
        },
        {
          title: "地理位置",
          dataIndex: "location",
          width: 180,
          align: "center"
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
          title: "设备类型",
          dataIndex: "type",
          width: 150,
          align: "center"
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
          scopedSlots: { customRender: "timeColumn" },
          width: 180,
          align: "center",
        },
        {
          title: "创建时间",
          dataIndex: "createTime",
          scopedSlots: { customRender: "timeColumn" },
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
      
      // 资源数据
      roleItems: [],
      
      // 设备数据
      data: [],
      cacheData: [],
      editingKey: "",
      
      // 加载状态标志
      clearMemoryLoading: false,
    };
  },
  
  mounted() {
      this.getRole()
      this.getData();
  },
  
  methods: {
    /**
     * 数据获取方法
     */
    // 获取设备列表数据
    getData() {
      this.loading = true;
      this.editingKey = "";
      
      // 构建查询参数
      const queryParams = {
        start: this.pagination.page,
        limit: this.pagination.pageSize,
        ...this.query,
      };
      
      // 添加过滤条件
      this.queryFilter.forEach(filter => {
        if (filter.value) {
          queryParams[filter.index] = filter.value;
        }
      });
      
      axios
        .get({
          url: api.device.query,
          data: queryParams,
        })
        .then((res) => {
          if (res.code === 200) {
            this.data = res.data.list;
            this.cacheData = res.data.list.map((item) => ({ ...item }));
            this.pagination.total = res.data.total;
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    },

    // 获取角色列表
    getRole() {
      return axios
        .get({ url: api.role.query, data: {} })
        .then((res) => {
          if (res.code === 200) {
            this.roleItems = res.data.list;
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        });
    },
    
    /**
     * 设备操作方法
     */
    // 添加设备
    addDevice(value) {
      if (!value) {
        this.$message.info("请输入设备编号");
        return;
      }
      
      if(this.roleItems.length == 0) {
        this.$message.warn("请先配置默认角色");
        return;
      }

      axios
        .post({
          url: api.device.add,
          data: {
            code: value
          },
        })
        .then((res) => {
          if (res.code === 200) {
            this.$message.success("设备添加成功");
            this.getData();
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        });
    },
    
    // 删除设备
    deleteDevice(record) {
      this.loading = true;
      axios
        .post({
          url: api.device.delete,
          data: { deviceId: record.deviceId }
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
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    },
    
    // 更新设备信息
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
            roleId: val.roleId
          }
        })
        .then((res) => {
          if (res.code === 200) {
            this.getData();
            this.editVisible = false;
            this.$message.success("修改成功");
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
          this.editingKey = "";
        });
    },
    
    // 清除设备记忆
    clearMemory(record) {
      this.clearMemoryLoading = true;
      axios
        .post({
          url: api.message.delete,
          data: { deviceId: record.deviceId }
        })
        .then((res) => {
          if (res.code === 200) {
            this.editVisible = false;
            this.$message.success("记忆清除成功");
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.clearMemoryLoading = false;
        });
    },
    
    // 在弹窗中编辑设备
    editWithDialog(device) {
      this.editVisible = true;
      this.currentDevice = { ...device };
    },
    
    // 选择变更处理函数
    handleSelectChange(value, key, type) {
      // 获取编辑中的数据行
      const data = this.editLine(key);
      
      if (type === "role") {
        const role = this.roleItems.find((item) => item.roleId === value);
        const name = role ? role.roleName : "";
        
        // 更新数据
        data.target.roleId = value;
        data.target.roleName = name;
        
        this.data = [...this.data]; // 强制更新视图
      }
    },
    
    // 获取角色名称
    getRoleName(roleId) {
      if (!roleId) return "";
      
      const role = this.roleItems.find(r => r.roleId === roleId);
      return role ? role.roleName : `角色ID:${roleId}`;
    }
  },
};
</script>

<style scoped>
/* 确保所有下拉框中的文本居中 */
>>> .ant-select-selection__rendered .ant-select-selection-selected-value {
  text-align: center !important;
  width: 100% !important;
}

/* 查询框中的下拉框保持默认对齐方式 */
>>> .table-search .ant-select-selection-selected-value {
  text-align: left !important;
}
</style>