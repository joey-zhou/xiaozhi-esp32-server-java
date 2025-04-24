<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 7 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xxl="6" :xl="6" :lg="12" :md="12" :xs="24" v-for="item in queryFilter" :key="item.index">
                <a-form-item :label="item.label">
                  <a-input-search v-model="query[item.index]" placeholder="请输入" allow-clear @search="getData()" />
                </a-form-item>
              </a-col>
              <a-col :xxl="6" :xl="6" :lg="12" :md="12" :xs="24">
                <a-form-item label="消息发送方">
                  <a-select v-model="query.sender" @change="getData()">
                    <a-select-option v-for="item in senderItems" :key="item.value">
                      <span>{{ item.label }}</span>
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :xxl="5" :xl="5" :lg="12" :md="12" :xs="24">
                <a-form-item label="对话日期">
                  <a-range-picker :ranges="{
                    今天: [moment().startOf('day'), moment().endOf('day')],
                    本月: [moment().startOf('month'), moment().endOf('month')],
                  }" :allowClear="false" :style="{ width: 100 }" v-model="timeRange" format="MM-DD"
                    @change="getData()" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        <!-- 表格数据 -->
        <a-card title="查询表格" :bodyStyle="{ padding: 0 }" :bordered="false">
          <a-table rowKey="messageId" :columns="tableColumns" :data-source="data" :loading="loading"
            :pagination="pagination" :scroll="{ x: 800 }" size="middle">
            <templace slot="roleName" slot-scope="text, record">
              <a-tooltip :title="record.roleDesc" :mouseEnterDelay="0.5" placement="leftTop">
                <span v-if="text">{{ text }}</span>
                <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
              </a-tooltip>
            </templace>
            <templace slot="message" slot-scope="text, record">
              <a-tooltip :title="text" :mouseEnterDelay="0.5" placement="leftTop">
                <span v-if="text">{{ text }}</span>
                <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
              </a-tooltip>
            </templace>
            <template slot="audioPath" slot-scope="text, record">
              <div v-if="text && text.trim()">
                <audio-player :audio-url="text" />
              </div>
              <span v-else>无音频</span>
            </template>
            <a-button slot="footer" :loading="exportLoading" :disabled="true" @click="exportExcel('message')">
              导出
            </a-button>
            <template slot="operation" slot-scope="text, record">
              <a-space>
                <a href="javascript:;" @click="edit(record.messageId)" :disabled="true">详情</a>
                <a-popconfirm
                  title="确定要删除此消息吗？相关联的对话也将被删除"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="deleteMessage(record)"
                >
                  <a href="javascript:;" style="color: #ff4d4f">删除</a>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table>
        </a-card>
      </div>
    </a-layout-content>
    <a-back-top />
  </a-layout>
</template>

<script>
import axios from "@/services/axios";
import api from "@/services/api";
import mixin from "@/mixins/index";
import AudioPlayer from "@/components/AudioPlayer.vue";
import EventBus from "@/utils/eventBus";

export default {
  mixins: [mixin],
  components: {
    AudioPlayer,
  },
  data() {
    return {
      // 查询框
      query: {
        sender: "",
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
      senderItems: [
        {
          label: "全部",
          value: "",
          key: "",
        },
        {
          label: "用户",
          value: "user",
          key: "1",
        },
        {
          label: "AI",
          value: "assistant",
          key: "0",
        },
      ],
      // 表格数据
      tableColumns: [
        {
          title: "设备编号",
          dataIndex: "deviceId",
          width: 160,
          align: "center",
        },
        {
          title: "设备名称",
          dataIndex: "deviceName",
          width: 100,
          align: "center",
        },
        {
          title: "模型角色",
          dataIndex: "roleName",
          scopedSlots: { customRender: "roleName" },
          width: 100,
          align: "center",
        },
        {
          title: "消息发送方",
          dataIndex: "sender",
          width: 100,
          align: "center",
          customRender: (text) => {
            return text === "user" ? "用户" : "AI";
          },
        },
        {
          title: "消息内容",
          dataIndex: "message",
          scopedSlots: { customRender: "message" },
          align: "center",
          width: 200,
          ellipsis: true,
        },
        {
          title: "语音",
          dataIndex: "audioPath",
          scopedSlots: { customRender: "audioPath" },
          width: 400,
          align: "center",
        },
        {
          title: "对话时间",
          dataIndex: "createTime",
          scopedSlots: { customRender: "createTime" },
          width: 180,
          align: "center",
        },
        {
          title: "操作",
          dataIndex: "operation",
          scopedSlots: { customRender: "operation" },
          width: 110,
          align: "center",
        },
      ],
      data: [],
    };
  },
  mounted() {
    this.getData();
  },
  beforeRouteLeave(to, from, next) {
    // 在路由离开前触发全局事件，通知所有音频播放器停止播放
    EventBus.$emit('stop-all-audio');
    next();
  },
  beforeDestroy() {
    // 在组件销毁前触发全局事件，通知所有音频播放器停止播放
    EventBus.$emit('stop-all-audio');
  },
  methods: {
    /* 查询参数列表 */
    getData() {
      this.loading = true;
      axios
        .get({
          url: api.message.query,
          data: {
            start: this.pagination.page,
            limit: this.pagination.pageSize,
            ...this.query,
            startTime: this.moment(this.timeRange[0]).format("YYYY-MM-DD HH:mm:ss"),
            endTime: this.moment(this.timeRange[1]).format("YYYY-MM-DD HH:mm:ss"),
          },
        })
        .then((res) => {
          if (res.code === 200) {
            this.data = res.data.list;
            this.pagination.total = res.data.total;
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.$message.error("服务器维护/重启中,请稍后再试");
        })
        .finally(() => {
          this.loading = false
        })
    },
  },
};
</script>
