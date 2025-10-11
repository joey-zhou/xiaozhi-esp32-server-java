<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xl="6" :lg="12" :xs="24">
                <a-form-item label="模板名称">
                  <a-input-search v-model="query.templateName" placeholder="请输入" allow-clear @search="pagination.page = 1; getData()" />
                </a-form-item>
              </a-col>
              <a-col :xl="6" :lg="12" :xs="24">
                <a-form-item label="分类">
                  <a-select v-model="query.category" @change="pagination.page = 1; getData()" placeholder="请选择分类">
                    <a-select-option key="" value="">
                      <span>全部</span>
                    </a-select-option>
                    <a-select-option v-for="item in categoryOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>

        <!-- 表格数据 -->
        <a-card title="提示词模板列表" :bodyStyle="{ padding: 0 }" :bordered="false">
          <!-- 将创建按钮移至卡片标题栏右侧 -->
          <a-button slot="extra" type="primary" @click="showCreateDialog">
            <a-icon type="plus" />创建模板
          </a-button>

          <a-table rowKey="templateId" :columns="tableColumns" :dataSource="data" :loading="loading"
            :pagination="pagination" :scroll="{ x: 800 }" size="middle">
            <!-- 模板内容 -->
            <template slot="templateContent" slot-scope="text, record">
              <a-tooltip :title="text" :mouseEnterDelay="0.5" placement="leftTop">
                <span v-if="text">{{ text }}</span>
                <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
              </a-tooltip>
            </template>
            <!-- 添加默认状态列的自定义渲染 -->
            <template slot="isDefault" slot-scope="text">
              <a-tag v-if="text == 1" color="green">默认</a-tag>
              <span v-else>-</span>
            </template>

            <!-- 操作 -->
            <template slot="operation" slot-scope="text, record">
              <a-space>
                <a @click="showEditDialog(record)">编辑</a>
                <!-- 该处预览后续可以尝试点击后与之对话，用于调试 -->
                <!-- todo -->
                <a @click="previewTemplate(record)">预览</a>
                <a-popconfirm title="确定要删除此模板吗？" ok-text="确定" cancel-text="取消" @confirm="deleteTemplate(record)">
                  <a style="color: #ff4d4f">删除</a>
                </a-popconfirm>
                <a v-if="record.isDefault != 1" @click="setAsDefault(record)">设为默认</a>
              </a-space>
            </template>
          </a-table>
        </a-card>
      </div>
    </a-layout-content>

    <!-- 创建/编辑模板对话框 -->
    <a-modal :visible="dialogVisible" :title="isEdit ? '编辑提示词模板' : '创建提示词模板'" @ok="handleSubmit" @cancel="closeDialog"
      :confirmLoading="submitLoading" width="800px" :maskClosable="false">
      <a-form :form="form" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="模板名称">
          <a-input v-decorator="[
            'templateName',
            {
              rules: [{ required: true, message: '请输入模板名称' }],
            },
          ]" placeholder="请输入模板名称" />
        </a-form-item>

        <a-form-item label="模板分类">
          <a-select v-decorator="[
            'category',
            {
              rules: [{ required: true, message: '请选择模板分类' }],
            },
          ]" placeholder="请选择模板分类" @change="handleCategoryChange">
            <a-select-option v-for="category in categoryOptions" :key="category.value" :value="category.value">
              {{ category.label }}
            </a-select-option>
            <a-select-option value="custom">自定义分类</a-select-option>
          </a-select>
        </a-form-item>

        <!-- 自定义分类输入框，当选择"自定义分类"时显示 -->
        <a-form-item label="自定义分类" v-if="showCustomCategory">
          <a-input v-decorator="[
            'customCategory',
            {
              rules: [{ required: true, message: '请输入自定义分类名称' }],
            },
          ]" placeholder="请输入自定义分类名称" />
        </a-form-item>

        <a-form-item label="模板描述">
          <a-input v-decorator="['templateDesc']" placeholder="请输入模板描述（简短描述角色性格等）" />
        </a-form-item>

        <a-form-item label="是否默认">
          <a-switch v-decorator="[
            'isDefault',
            { valuePropName: 'checked' },
          ]" />
          <span style="margin-left: 8px; color: #999">设为默认后将优先显示此模板</span>
        </a-form-item>

        <a-form-item label="模板内容">
          <a-textarea v-decorator="[
            'templateContent',
            {
              rules: [{ required: true, message: '请输入模板内容' }],
            },
          ]" :rows="12" placeholder="请输入模板内容，描述角色的特点、知识背景和行为方式等" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 预览模板对话框 -->
    <a-modal :visible="previewVisible" title="模板预览" @cancel="previewVisible = false" :footer="null" width="700px">
      <div v-if="currentTemplate">
        <h3>{{ currentTemplate.templateName }}</h3>
        <p v-if="currentTemplate.templateDesc" class="template-desc">{{ currentTemplate.templateDesc }}</p>
        <a-divider />
        <div class="template-preview-content">
          {{ currentTemplate.templateContent }}
        </div>
      </div>
    </a-modal>
  </a-layout>
</template>

<script>
import axios from "@/services/axios";
import api from "@/services/api";
import mixin from "@/mixins/index";

export default {
  name: "PromptTemplate",
  mixins: [mixin],
  data() {
    return {
      // 查询参数
      query: {
        templateName: "",
        category: ""
      },
      // 表格列定义
      tableColumns: [
        {
          title: "模板名称",
          dataIndex: "templateName",
          scopedSlots: { customRender: "templateName" },
          width: 100,
          align: "center",
        },
        {
          title: "分类",
          dataIndex: "category",
          width: 120,
          align: "center",
        },
        {
          title: "模板内容",
          dataIndex: "templateContent",
          scopedSlots: { customRender: "templateContent" },
          width: 200,
          align: "center",
        },
        // 添加默认标识列
        {
          title: "默认",
          dataIndex: "isDefault",
          key: "isDefault",
          width: 80,
          align: "center",
          scopedSlots: { customRender: "isDefault" },
          align: "center",
        },
        {
          title: "创建时间",
          dataIndex: "createTime",
          width: 180,
          align: "center",
        },
        {
          title: "操作",
          dataIndex: "operation",
          scopedSlots: { customRender: "operation" },
          width: 220,
          fixed: "right",
          align: "center",
        },
      ],
      // 分类选项
      categoryOptions: [
        { label: "基础角色", value: "基础角色" },
        { label: "专业角色", value: "专业角色" },
        { label: "社交角色", value: "社交角色" },
        { label: "娱乐角色", value: "娱乐角色" }
      ],
      // 表格数据
      data: [],
      // 对话框相关
      dialogVisible: false,
      isEdit: false,
      submitLoading: false,
      showCustomCategory: false,
      form: null,
      // 预览相关
      previewVisible: false,
      currentTemplate: null,
    };
  },
  created() {
    this.form = this.$form.createForm(this);
  },
  mounted() {
    this.getData();
  },
  methods: {
    // 获取模板数据
    getData() {
      this.loading = true;

      axios.get({
        url: api.template.query,
        data: {
          ...this.query,
          start: this.pagination.page,
          limit: this.pagination.pageSize
        }
      })
        .then(res => {
          if (res.code === 200) {
            this.data = res.data.list || [];
            this.pagination.total = res.data.total || 0;

            // 收集所有分类
            const categories = new Set();
            this.data.forEach(item => {
              if (item.category) {
                categories.add(item.category);
              }
            });

            // 更新分类选项
            const defaultCategories = ["基础角色", "专业角色", "社交角色", "娱乐角色"];
            const customCategories = [...categories].filter(c => !defaultCategories.includes(c));

            if (customCategories.length > 0) {
              this.categoryOptions = [
                ...defaultCategories.map(c => ({ label: c, value: c })),
                ...customCategories.map(c => ({ label: c, value: c }))
              ];
            }
          } else {
            this.$message.error(res.message || "获取模板列表失败");
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    },

    // 显示创建对话框
    showCreateDialog() {
      this.isEdit = false;
      this.dialogVisible = true;
      this.showCustomCategory = false;

      // 重置表单
      this.$nextTick(() => {
        this.form.resetFields();
        this.form.setFieldsValue({
          category: "基础角色",
          isDefault: false
        });
      });
    },

    // 显示编辑对话框
    showEditDialog(record) {
      this.isEdit = true;
      this.dialogVisible = true;
      this.currentTemplate = { ...record };

      // 检查是否需要显示自定义分类输入框
      this.showCustomCategory = record.category &&
        !this.categoryOptions.some(c => c.value === record.category);

      // 设置表单值
      this.$nextTick(() => {
        this.form.resetFields();

        if (this.showCustomCategory) {
          this.form.setFieldsValue({
            templateName: record.templateName,
            category: "custom",
            customCategory: record.category,
            templateDesc: record.templateDesc || "",
            templateContent: record.templateContent,
            isDefault: record.isDefault == 1
          });
        } else {
          this.form.setFieldsValue({
            templateName: record.templateName,
            category: record.category,
            templateDesc: record.templateDesc || "",
            templateContent: record.templateContent,
            isDefault: record.isDefault == 1
          });
        }
      });
    },

    // 关闭对话框
    closeDialog() {
      this.dialogVisible = false;
      this.isEdit = false;
      this.showCustomCategory = false;
    },

    // 处理分类变化
    handleCategoryChange(value) {
      this.showCustomCategory = value === "custom";
    },

    // 提交表单
    handleSubmit() {
      this.form.validateFields((err, values) => {
        if (err) return;

        this.submitLoading = true;

        // 处理自定义分类
        let category = values.category;
        if (category === "custom" && values.customCategory) {
          category = values.customCategory;
        }

        // 构建请求数据
        const requestData = {
          templateName: values.templateName,
          templateDesc: values.templateDesc || "",
          category: category,
          templateContent: values.templateContent,
          isDefault: values.isDefault ? 1 : 0,
          state: 1
        };

        // 如果是编辑模式，添加templateId
        if (this.isEdit && this.currentTemplate) {
          requestData.templateId = this.currentTemplate.templateId;
        }

        // 确定API端点
        const url = this.isEdit ? api.template.update : api.template.add;

        // 发送请求
        axios.post({
          url,
          data: requestData
        })
          .then(res => {
            if (res.code === 200) {
              this.$message.success(this.isEdit ? "模板更新成功" : "模板创建成功");
              this.closeDialog();
              this.getData();
            } else {
              this.$message.error(res.message || "操作失败");
            }
          })
          .catch(() => {
            this.showError();
          })
          .finally(() => {
            this.submitLoading = false;
          });
      });
    },

    // 预览模板
    previewTemplate(record) {
      this.currentTemplate = record;
      this.previewVisible = true;
    },

    // 删除模板
    deleteTemplate(record) {
      this.loading = true;

      axios.post({
        url: api.template.update,
        data: {
          templateId: record.templateId,
          state: '0'
        }
      })
        .then(res => {
          if (res.code === 200) {
            this.$message.success("模板已删除");
            this.getData();
          } else {
            this.$message.error(res.message || "删除失败");
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    },

    // 设为默认模板
    setAsDefault(record) {
      this.loading = true;

      axios.post({
        url: api.template.update,
        data: {
          templateId: record.templateId,
          isDefault: 1
        }
      })
        .then(res => {
          if (res.code === 200) {
            this.$message.success("已设为默认模板");
            this.getData();
          } else {
            this.$message.error(res.message || "操作失败");
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.loading = false;
        });
    }
  }
};
</script>

<style scoped>
.template-preview-content {
  white-space: pre-wrap;
  background: #f5f5f5;
  padding: 16px;
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
}

.template-desc {
  color: #666;
  font-style: italic;
}
</style>