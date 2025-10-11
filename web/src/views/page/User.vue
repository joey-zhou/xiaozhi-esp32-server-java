<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form
            layout="horizontal"
            :colon="false"
            :labelCol="{ span: 6 }"
            :wrapperCol="{ span: 16 }"
          >
            <a-row class="filter-flex">
              <a-col
                :xl="6"
                :lg="12"
                :xs="24"
                v-for="item in queryFilter"
                :key="item.index"
              >
                <a-form-item :label="item.label">
                  <a-input-search
                    v-model="query[item.index]"
                    placeholder="请输入"
                    allow-clear
                    @search="pagination.page = 1; getData()"
                  />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        <!-- 表格数据 -->
        <a-card title="用户管理" :bodyStyle="{ padding: 0 }" :bordered="false">
          <a-table
            rowKey="userId"
            :columns="tableColumns"
            :data-source="data"
            :loading="loading"
            :pagination="pagination"
            :scroll="{ x: 1200 }"
            size="middle"
          >
            <!-- 头像 -->
            <template slot="avatar" slot-scope="text">
              <a-avatar :src="getAvatarUrl(text)" />
            </template>
            <!-- 状态 -->
            <template slot="state" slot-scope="text">
              <a-tag color="green" v-if="text == 1">正常</a-tag>
              <a-tag color="red" v-else>禁用</a-tag>
            </template>

            <!-- 是否管理员 -->
            <template slot="isAdmin" slot-scope="text">
              <a-tag color="blue" v-if="text == 1">管理员</a-tag>
              <a-tag v-else>用户</a-tag>
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
import { getResourceUrl } from "@/services/axios";

export default {
  mixins: [mixin],
  data() {
    return {
      // 查询框
      query: {},
      queryFilter: [
        {
          label: "姓名",
          value: "",
          index: "name",
        },
        {
          label: "邮箱",
          value: "",
          index: "email",
        },
        {
          label: "电话",
          value: "",
          index: "tel",
        },
      ],
      // 表格数据
      tableColumns: [
        {
          title: "姓名",
          dataIndex: "name",
          width: 100,
          scopedSlots: { customRender: "name" },
          fixed: "left",
          align: "center",
        },
        {
          title: "用户头像",
          dataIndex: "avatar",
          width: 80,
          scopedSlots: { customRender: "avatar" },
          fixed: "left",
          align: "center",
        },
        {
          title: "邮箱",
          dataIndex: "email",
          width: 180,
          align: "center",
        },
        {
          title: "电话",
          dataIndex: "tel",
          width: 150,
          align: "center",
        },
        {
          title: "设备数量",
          dataIndex: "totalDevice",
          width: 100,
          align: "center",
        },
        {
          title: "在线设备",
          dataIndex: "aliveNumber",
          width: 100,
          align: "center",
        },
        {
          title: "对话消息数",
          dataIndex: "totalMessage",
          width: 120,
          align: "center",
        },
        {
          title: "状态",
          dataIndex: "state",
          scopedSlots: { customRender: "state" },
          width: 80,
          align: "center",
        },
        {
          title: "账户类型",
          dataIndex: "isAdmin",
          scopedSlots: { customRender: "isAdmin" },
          width: 80,
          align: "center",
        },
        {
          title: "最后登录时间",
          dataIndex: "loginTime",
          width: 180,
          align: "center",
        },
        {
          title: "最后登录IP",
          dataIndex: "loginIp",
          width: 150,
          align: "center",
        },
      ],
      data: [],
    };
  },
  mounted() {
    this.getData();
  },
  methods: {
    getAvatarUrl(avatar) {
      return getResourceUrl(avatar);
    },
    /* 查询用户列表 */
    getData() {
      this.loading = true;
      axios
        .get({
          url: api.user.queryUsers,
          data: {
            start: this.pagination.page,
            limit: this.pagination.pageSize,
            ...this.query,
          },
        })
        .then((res) => {
          if (res.code === 200) {
            this.data = res.data.list;
            this.pagination.total = res.data.total;
          } else {
            this.showError(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    },
    
  },
};
</script>