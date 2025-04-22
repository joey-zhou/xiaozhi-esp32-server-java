<template>
  <a-layout>
    <a-layout-content>
      <div class="layout-content-margin">
        <!-- 查询框 -->
        <div class="table-search">
          <a-form layout="horizontal" :colon="false" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-row class="filter-flex">
              <a-col :xxl="8" :xl="8" :lg="12" :xs="24">
                <a-form-item :label="`类别`">
                  <a-select v-model="query.provider" @change="getData()">
                    <a-select-option key="" value="">
                      <span>全部</span>
                    </a-select-option>
                    <a-select-option v-for="item in typeOptions" :key="item.value">
                      <span>{{ item.label }}</span>
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :xl="8" :lg="12" :xs="24" v-for="item in queryFilter" :key="item.index">
                <a-form-item :label="item.label">
                  <a-input-search v-model="query[item.index]" placeholder="请输入" allow-clear @search="getData()" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        <!-- 表格数据 -->
        <a-card :bodyStyle="{ padding: 0 }" :bordered="false">
          <a-tabs defaultActiveKey="1" :activeKey="activeTabKey" @change="handleTabChange"
            tabBarStyle="margin: 0 0 0 15px">
            <a-tab-pane key="1" :tab="`${configTypeInfo.label}列表`">
              <a-table :columns="columns" :dataSource="configItems" :loading="loading" :pagination="pagination"
                rowKey="configId" :scroll="{ x: 800 }" size="middle">
                <template slot="configDesc" slot-scope="text">
                  <a-tooltip :title="text" :mouseEnterDelay="0.5" placement="leftTop">
                    <span v-if="text">{{ text }}</span>
                    <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                  </a-tooltip>
                </template>
                <template slot="operation" slot-scope="text, record">
                  <a-space>
                    <a @click="edit(record)">编辑</a>
                    <a-popconfirm :title="`确定要删除这个${configTypeInfo.label}配置吗?`" @confirm="update(record.configId)">
                      <a>删除</a>
                    </a-popconfirm>
                  </a-space>
                </template>
              </a-table>
            </a-tab-pane>
            <a-tab-pane key="2" :tab="`创建${configTypeInfo.label}`">
              <a-form layout="horizontal" :form="configForm" :colon="false" @submit="handleSubmit"
                style="padding: 10px 24px">
                <a-row :gutter="20">
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item :label="`${configTypeInfo.label}类别`">
                      <a-select v-decorator="[
                        'provider',
                        { rules: [{ required: true, message: `请选择${configTypeInfo.label}类别` }] }
                      ]" :placeholder="`请选择${configTypeInfo.label}类别`" @change="handleTypeChange">
                        <a-select-option v-for="item in typeOptions" :key="item.value" :value="item.value">
                          {{ item.label }}
                        </a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                  <a-col :xl="16" :lg="12" :xs="24">
                    <a-form-item :label="`${configTypeInfo.label}名称`">
                      <!-- 修改这里，添加模型名称提示 -->
                      <a-tooltip 
                        v-if="configType === 'llm' && currentType && getModelNameTip(currentType)"
                        :title="getModelNameTip(currentType)"
                        placement="top">
                        <a-input v-decorator="[
                          'configName',
                          { rules: [{ required: true, message: `请输入${configTypeInfo.label}名称` }] }
                        ]" autocomplete="off" :placeholder="`请输入${configTypeInfo.label}名称`" />
                      </a-tooltip>
                      <a-input 
                        v-else
                        v-decorator="[
                          'configName',
                          { rules: [{ required: true, message: `请输入${configTypeInfo.label}名称` }] }
                        ]" autocomplete="off" :placeholder="`请输入${configTypeInfo.label}名称`" />
                    </a-form-item>
                  </a-col>
                </a-row>
                <a-form-item :label="`${configTypeInfo.label}描述`">
                  <a-textarea v-decorator="['configDesc']" :placeholder="`请输入${configTypeInfo.label}描述`" :rows="4" />
                </a-form-item>

                <a-divider>参数配置</a-divider>
                <a-space direction="vertical" style="width: 100%">
                  <a-card v-if="currentType" size="small" :bodyStyle="{ 'background-color': '#fafafa' }"
                    :bordered="false">
                    <a-row :gutter="20">
                      <!-- 根据选择的模型类别动态显示参数配置 -->
                      <template v-for="field in currentTypeFields">
                        <a-col :key="field.name" :xl="field.span || 12" :lg="12" :xs="24">
                          <a-form-item :label="field.label" style="margin-bottom: 24px">
                            <a-input v-decorator="[
                              field.name,
                              { rules: [{ required: field.required, message: `请输入${field.label}` }] }
                            ]" :placeholder="`请输入${field.label}`" :type="field.inputType || 'text'">
                              <template v-if="field.suffix" slot="suffix">
                                <span style="color: #999">{{ field.suffix }}</span>
                              </template>
                            </a-input>
                          </a-form-item>
                        </a-col>
                      </template>
                    </a-row>
                  </a-card>
                  <a-card v-else :bodyStyle="{ 'background-color': '#fafafa' }" :bordered="false">
                    <a-empty :description="`请先选择${configTypeInfo.label}类别`" />
                  </a-card>

                  <a-form-item>
                    <a-button type="primary" html-type="submit">
                      {{ editingConfigId ? `更新${configTypeInfo.label}` : `创建${configTypeInfo.label}` }}
                    </a-button>
                    <a-button style="margin-left: 8px" @click="resetForm">
                      取消
                    </a-button>
                  </a-form-item>
                </a-space>
              </a-form>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </div>
    </a-layout-content>
  </a-layout>
</template>

<script>
import axios from '@/services/axios'
import api from '@/services/api'
import mixin from '@/mixins/index'

export default {
  name: 'ConfigManager',
  mixins: [mixin],
  props: {
    // 配置类型：llm, stt, tts
    configType: {
      type: String,
      required: true,
      validator: value => ['llm', 'stt', 'tts'].includes(value)
    }
  },
  data() {
    return {
      // 查询框
      query: {
        provider: "",
      },
      queryFilter: [
        {
          label: "名称",
          value: "",
          index: "configName",
        },
      ],
      activeTabKey: '1', // 当前激活的标签页
      configForm: null,
      configItems: [],
      editingConfigId: null,
      currentType: '',
      loading: false,

      // 模型名称提示信息
      modelNameTips: {
        openai: "请输入要调用的模型名称，如：gpt-3.5-turbo, gpt-4, qwen-max, deepseek-chat等",
        ollama: "请输入要调用的Ollama模型名称，如：deepseek-r1, qwen2.5:7b, gemma3:12b等",
        spark: "请输入星火大模型官方模型名称，如：Lite, Pro, Max等"
      },

      // 配置类型信息
      configTypeMap: {
        llm: {
          label: '模型',
          // 模型类别选项
          typeOptions: [
            { label: 'OpenAI', value: 'openai', key: '0' },
            { label: 'Ollama', value: 'ollama', key: '1' },
            { label: 'Spark', value: 'spark', key: '2' }
          ],
          // 各类别对应的参数字段定义
          typeFields: {
            openai: [
              { name: 'apiKey', label: 'API Key', required: true, span: 12 },
              { name: 'apiUrl', label: 'API URL', required: false, span: 12, suffix: '/chat/completions' },
            ],
            ollama: [
              { name: 'apiUrl', label: 'API URL', required: false, span: 12, suffix: '/api/chat' }
            ],
            spark: [
              { name: 'apiSecret', label: 'API Secret', required: true, span: 8 },
              { name: 'apiUrl', label: 'API URL', required: false, span: 12, suffix: '/chat/completions', defaultUrl:"https://spark-api-open.xf-yun.com/v2" }
            ]
          }
        },
        stt: {
          label: '语音识别',
          typeOptions: [
            { label: 'Tencent', value: 'tencent', key: '0' },
            { label: 'Aliyun', value: 'aliyun', key: '1' }
          ],
          typeFields: {
            tencent: [
              { name: 'appId', label: 'App Id', required: true, span: 12 },
              { name: 'apiKey', label: 'Secret Id', required: true, span: 12 },
              { name: 'apiSecret', label: 'Secret Key', required: true, span: 12 },
            ],
            aliyun: [
              { name: 'apiKey', label: 'API Key', required: true, span: 12 },
            ]
          }
        },
        tts: {
          label: '语音合成',
          typeOptions: [
            { label: 'Tencent', value: 'tencent', key: '0' },
            { label: 'Aliyun', value: 'aliyun', key: '1' },
            { label: 'Volcengine(doubao)', value: 'volcengine', key: '2' }
          ],
          typeFields: {
            tencent: [
              { name: 'appId', label: 'App Id', required: true, span: 12 },
              { name: 'apiKey', label: 'Secret Id', required: true, span: 12 },
              { name: 'apiSecret', label: 'Secret Key', required: true, span: 12 },
            ],
            aliyun: [
              { name: 'apiKey', label: 'API Key', required: true, span: 12 },
            ],
            volcengine: [
              { name: 'appId', label: 'App Id', required: true, span: 12 },
              { name: 'apiKey', label: 'Access Token', required: true, span: 12 }
            ]
          }
        }
      },
      columns: [
        {
          title: '类别',
          dataIndex: 'provider',
          key: 'provider',
          width: 200,
          align: 'center',
          customRender: (text) => {
            const provider = this.typeOptions.find(item => item.value === text);
            return provider ? provider.label : text;
          },
          ellipsis: true,
        },
        {
          title: '名称',
          dataIndex: 'configName',
          key: 'configName',
          width: 200,
          align: 'center',
        },
        {
          title: '描述',
          dataIndex: 'configDesc',
          scopedSlots: { customRender: 'configDesc' },
          key: 'configDesc',
          align: 'center',
          ellipsis: true,
        },
        {
          title: '创建时间',
          dataIndex: 'createTime',
          key: 'createTime',
          width: 180,
          align: 'center'
        },
        {
          title: '操作',
          dataIndex: 'operation',
          key: 'operation',
          width: 140,
          align: 'center',
          fixed: 'right',
          scopedSlots: { customRender: 'operation' }
        }
      ]
    }
  },
  computed: {
    // 当前配置类型的信息
    configTypeInfo() {
      return this.configTypeMap[this.configType] || {};
    },
    // 当前配置类型的选项
    typeOptions() {
      return this.configTypeInfo.typeOptions || [];
    },
    // 当前选择的类别对应的参数字段
    currentTypeFields() {
      const typeFieldsMap = this.configTypeInfo.typeFields || {};
      return typeFieldsMap[this.currentType] || [];
    }
  },
  created() {
    // 创建表单实例
    this.configForm = this.$form.createForm(this, {
      onValuesChange: (props, values) => {
        if (values.provider && values.provider !== this.currentType) {
          this.currentType = values.provider;
        }
      }
    });
  },
  mounted() {
    this.getData()
  },
  methods: {
    // 获取模型名称提示
    getModelNameTip(providerType) {
      return this.configType === 'llm' ? this.modelNameTips[providerType] : null;
    },
    
    // 处理标签页切换
    handleTabChange(key) {
      this.activeTabKey = key;
      this.resetForm();
    },

    // 处理类别变化
    handleTypeChange(value) {
      console.log('选择的类别:', value);
      this.currentType = value;

      // 由于使用了v-decorator，不需要手动设置表单值
      // 但需要清除之前的参数值
      const { configForm } = this;
      const formValues = configForm.getFieldsValue();

      // 创建一个新的表单值对象，只保留基本信息
      const newValues = {
        provider: value,
        configName: formValues.configName,
        configDesc: formValues.configDesc
      };

      // 清除所有可能的参数字段
      const allParamFields = ['apiKey', 'apiUrl', 'apiSecret', 'appId', 'secretKey', 'region'];
      allParamFields.forEach(field => {
        newValues[field] = undefined;
      });

      //填写llm默认url
      if(this.configType === 'llm') {
        newValues.apiUrl = this.configTypeMap.llm.typeFields[value].find(item=>item.name === 'apiUrl').defaultUrl
      }

      // 重置表单
      this.$nextTick(() => {
        // 设置新的表单值
        configForm.setFieldsValue(newValues);
      });
    },

    // 获取配置列表
    getData() {
      this.loading = true;
      axios
        .get({
          url: api.config.query,
          data: {
            page: this.pagination.page,
            pageSize: this.pagination.pageSize,
            configType: this.configType,
            ...this.query
          }
        })
        .then(res => {
          if (res.code === 200) {
            this.configItems = res.data.list
            this.pagination.total = res.data.total
          } else {
            this.$message.error(res.message)
          }
        })
        .catch(() => {
          this.$message.error('服务器维护/重启中，请稍后再试')
        })
        .finally(() => {
          this.loading = false
        })
    },

    // 提交表单
    handleSubmit(e) {
      e.preventDefault()
      this.configForm.validateFields((err, values) => {
        if (!err) {
          this.loading = true

          const url = this.editingConfigId
            ? api.config.update
            : api.config.add

          axios
            .post({
              url,
              data: {
                configId: this.editingConfigId,
                configType: this.configType,
                ...values
              }
            })
            .then(res => {
              if (res.code === 200) {
                this.$message.success(
                  this.editingConfigId ? '更新成功' : '创建成功'
                )
                this.resetForm()
                this.getData()
                // 成功后切换到列表页
                this.activeTabKey = '1'
              } else {
                this.$message.error(res.message)
              }
            })
            .catch(() => {
              this.$message.error('服务器维护/重启中，请稍后再试')
            })
            .finally(() => {
              this.loading = false
            })
        }
      })
    },

    // 编辑配置
    edit(record) {
      this.editingConfigId = record.configId
      this.currentType = record.provider || '';

      // 切换到创建配置标签页
      this.activeTabKey = '2'

      this.$nextTick(() => {
        const { configForm } = this

        // 设置表单值
        configForm.setFieldsValue({
          ...record
        });
      })
    },

    // 删除配置
    update(configId) {
      this.loading = true
      axios
        .post({
          url: api.config.update,
          data: {
            configId: configId,
            state: 0
          }
        })
        .then(res => {
          if (res.code === 200) {
            this.$message.success(`删除${this.configTypeInfo.label}配置成功`)
            this.getData()
          } else {
            this.$message.error(res.message)
          }
        })
        .catch(() => {
          this.$message.error('服务器维护/重启中，请稍后再试')
        })
        .finally(() => {
          this.loading = false
        })
    },

    // 重置表单
    resetForm() {
      this.configForm.resetFields()
      this.currentType = ''
      this.editingConfigId = null
    }
  }
}
</script>