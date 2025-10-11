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
                  <a-input-search v-model="query[item.index]" placeholder="请输入" allow-clear @search="pagination.page = 1; getData()" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </div>
        <!-- 表格数据 -->
        <a-card :bodyStyle="{ padding: 0 }" :bordered="false">
          <a-tabs defaultActiveKey="1" :activeKey="activeTabKey" @change="handleTabChange"
            :tabBarStyle="{ margin: '0 0 0 15px' }">
            <a-tab-pane key="1" tab="角色列表">
              <a-table :columns="columns" :dataSource="roleItems" :loading="loading" :pagination="pagination"
                rowKey="roleId" :scroll="{ x: 1000 }" size="middle">
                <!-- 头像列 -->
                <template slot="avatar" slot-scope="text, record">
                  <a-avatar :src="getAvatarUrl(record.avatar)" icon="user" :size="40" />
                </template>
                <!-- 角色描述列 -->
                <template slot="roleDesc" slot-scope="text, record">
                  <a-tooltip :title="text" :mouseEnterDelay="0.5" placement="leftTop">
                    <span v-if="text">{{ text }}</span>
                    <span v-else style="padding: 0 50px">&nbsp;&nbsp;&nbsp;</span>
                  </a-tooltip>
                </template>
                
                <!-- 默认状态列 -->
                <template slot="isDefault" slot-scope="text">
                  <a-tag v-if="text == 1" color="green">默认</a-tag>
                  <span v-else>-</span>
                </template>
                
                <!-- 模型列 -->
                <template slot="modelName" slot-scope="text, record">
                  <a-tooltip :title="record.modelDesc || ''" :mouseEnterDelay="0.5">
                    <span v-if="record.modelId && record.modelName">
                      {{ record.modelName }}
                      <a-tag v-if="record.modelType === 'agent'" color="blue" size="small">智能体</a-tag>
                      <a-tag v-if="record.modelProvider" color="green" size="small">{{ record.modelProvider }}</a-tag>
                    </span>
                    <span v-else>-</span>
                  </a-tooltip>
                </template>
                
                <!-- 语音识别列 -->
                <template slot="sttName" slot-scope="text, record">
                  <a-tooltip :title="record.sttDesc || ''" :mouseEnterDelay="0.5">
                    <span v-if="record.sttId">{{ record.sttName || getItemName(sttItems, "sttId", record.sttId, "sttName") }}</span>
                    <span v-else>Vosk本地识别</span>
                  </a-tooltip>
                </template>
                
                <!-- 语音合成列 -->
                <template slot="voiceName" slot-scope="text, record">
                  <span v-if="text">
                    {{ getVoiceDisplayName(text, record.ttsProvider) }}

                    <a-tag :color="getVoiceTagColor(record.ttsProvider)" size="small">{{ formatProviderName(record.ttsProvider) }}</a-tag>
                  </span>
                  <span v-else>-</span>
                </template>
                
                <!-- 操作列 -->
                <template slot="operation" slot-scope="text, record">
                  <a-space>
                    <a @click="edit(record)">编辑</a>
                    <!-- 设为默认按钮 -->
                    <a v-if="record.isDefault != 1" href="javascript:" :disabled="record.isDefault == 1"
                      @click="setAsDefault(record)">设为默认</a>
                    <a-popconfirm title="确定要删除这个角色吗?" @confirm="update(record.roleId, '0')">
                      <a href="javascript:" style="color: #ff4d4f">删除</a>
                    </a-popconfirm>
                  </a-space>
                </template>
              </a-table>
            </a-tab-pane>
            
            <a-tab-pane key="2" tab="创建角色">
              <a-form layout="horizontal" :form="roleForm" :colon="false" @submit="handleSubmit"
                style="padding: 10px 24px">
                <!-- 基本信息区域 -->
                <a-row :gutter="20">
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item label="角色头像">
                      <div class="avatar-uploader-wrapper">
                        <!-- 整个区域都可点击的上传组件 -->
                        <a-upload
                          name="file"
                          :show-upload-list="false"
                          :before-upload="beforeAvatarUpload"
                          accept=".jpg,.jpeg,.png,.gif"
                          class="avatar-uploader"
                        >
                          <div class="avatar-content">
                            <!-- 有头像时显示头像 -->
                            <!-- <img v-if="avatarUrl" :src="getAvatarUrl(avatarUrl)" alt="角色头像" class="avatar-image" /> -->
                            <a-avatar v-if="avatarUrl" :size="128" :src="getAvatarUrl(avatarUrl)" icon="user" />
                            <!-- 无头像时显示上传图标 -->
                            <div v-else class="avatar-placeholder">
                              <a-icon type="user" />
                              <p>点击上传</p>
                            </div>
                            
                            <!-- 悬浮提示层，整个区域都会显示 -->
                            <div class="avatar-hover-mask">
                              <a-icon :type="avatarLoading ? 'loading' : 'camera'" />
                              <p>{{ avatarUrl ? '更换头像' : '上传头像' }}</p>
                            </div>
                          </div>
                        </a-upload>
                        
                        <!-- 如果有头像，显示删除按钮 -->
                        <a-button 
                          v-if="avatarUrl" 
                          type="danger" 
                          size="small"
                          @click.stop="removeAvatar"
                          class="avatar-remove-btn"
                        >
                          <a-icon type="delete" /> 移除头像
                        </a-button>
                        
                        <div class="avatar-tip">
                          支持JPG、PNG、GIF格式，不超过2MB
                        </div>
                      </div>
                    </a-form-item>
                  </a-col>
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item label="角色名称">
                      <a-input v-decorator="[
                        'roleName',
                        {
                          rules: [
                            { required: true, message: '请输入角色名称' },
                          ],
                        },
                      ]" autocomplete="off" placeholder="请输入角色名称" />
                    </a-form-item>
                  </a-col>
                  
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item label="设为默认角色">
                      <a-switch v-decorator="[
                        'isDefault',
                        { valuePropName: 'checked', initialValue: false },
                      ]" />
                      <span style="margin-left: 8px; color: #999">设为默认后将优先使用此角色</span>
                    </a-form-item>
                  </a-col>
                </a-row>

                <!-- 模型设置区域 -->
                <a-divider orientation="left">模型设置</a-divider>
                
                <a-row :gutter="20">
                  <!-- 模型类型选择 -->
                  <a-col :xl="8" :lg="24">
                    <a-form-item label="模型类型">
                      <a-radio-group v-decorator="['modelType', { initialValue: 'llm' }]" 
                        @change="handleModelTypeChange">
                        <a-radio-button value="llm">LLM模型</a-radio-button>
                        <a-radio-button value="agent">智能体</a-radio-button>
                      </a-radio-group>
                    </a-form-item>
                  </a-col>
                  
                  <!-- 提供商选择 -->
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item label="提供商">
                      <a-select v-decorator="['modelProvider']" placeholder="请选择提供商" 
                        @change="handleProviderChangeForModel" :disabled="!selectedModelType">
                        <!-- LLM提供商选项 -->
                        <a-select-option v-if="selectedModelType === 'llm'" 
                          v-for="provider in llmProviders" :key="provider" :value="provider">
                          {{ formatProviderName(provider) }}
                        </a-select-option>
                        <!-- 智能体提供商选项 -->
                        <a-select-option v-if="selectedModelType === 'agent'" value="coze">Coze</a-select-option>
                        <a-select-option v-if="selectedModelType === 'agent'" value="dify">Dify</a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                  
                  <!-- 模型选择 -->
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item label="模型">
                      <a-select v-decorator="[
                        'modelId',
                        {
                          rules: [
                            { required: true, message: '请选择模型' },
                          ],
                        },
                      ]" placeholder="请选择模型" @change="handleModelChange" 
                        :disabled="!selectedModelProvider" :loading="modelLoading">
                        <!-- LLM模型选项 -->
                        <a-select-option v-if="selectedModelType === 'llm'" 
                          v-for="model in filteredModels" :key="model.configId" :value="model.configId">
                          {{ model.configName }}
                        </a-select-option>
                        <!-- 智能体选项 -->
                        <a-select-option v-if="selectedModelType === 'agent'" 
                          v-for="agent in filteredAgents" :key="agent.configId" :value="agent.configId">
                          {{ agent.agentName }}
                        </a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                </a-row>
                
                <!-- 模型高级设置 -->
                <a-collapse :bordered="false" style="background: transparent; margin-bottom: 24px;" @change="handleModelCollapseChange">
                  <a-collapse-panel header="模型高级设置" key="1">
                    <a-row :gutter="16">
                      <a-col :xl="8" :lg="12" :xs="24">
                        <a-form-item label="温度 (Temperature)" :labelCol="{ span: 10 }" :wrapperCol="{ span: 14 }">
                          <a-tooltip placement="top">
                            <template slot="title">
                              <div>控制回答的创造性：</div>
                              <div>- 低值(0.2)：更精确保守</div>
                              <div>- 高值(0.8)：更有创意多样</div>
                            </template>
                            <a-input-number 
                              v-decorator="[
                                'temperature',
                                { initialValue: 0.7 }
                              ]" 
                              :min="0" 
                              :max="2" 
                              :step="0.1" 
                              style="width: 100%" 
                            />
                          </a-tooltip>
                        </a-form-item>
                      </a-col>
                      <a-col :xl="8" :lg="12" :xs="24">
                        <a-form-item label="核心采样 (Top-P)" :labelCol="{ span: 10 }" :wrapperCol="{ span: 14 }">
                          <a-tooltip placement="top">
                            <template slot="title">
                              <div>控制词汇选择范围：</div>
                              <div>- 低值(0.5)：更聚焦</div>
                              <div>- 高值(0.9)：考虑更多可能性</div>
                              <div>注：不建议与高温度同时使用，
                              会导致输出过于随机，
                              甚至出现胡言乱语</div>
                            </template>
                            <a-input-number 
                              v-decorator="[
                                'topP',
                                { initialValue: 0.9 }
                              ]" 
                              :min="0" 
                              :max="1" 
                              :step="0.05" 
                              style="width: 100%" 
                            />
                          </a-tooltip>
                        </a-form-item>
                      </a-col>
                    </a-row>
                  </a-collapse-panel>
                </a-collapse>

                <!-- 语音识别设置区域 -->
                <a-divider orientation="left">语音识别设置</a-divider>

                <a-row :gutter="20">
                  <!-- 语音识别配置选择 -->
                  <a-col :xl="8" :lg="12" :xs="24">
                    <a-form-item label="语音识别">
                      <a-select v-decorator="[
                        'sttId',
                        {
                          initialValue: -1,
                          rules: [
                            { required: true, message: '请选择语音识别配置' },
                          ],
                        },
                      ]" placeholder="请选择语音识别配置" :loading="sttConfigLoading">
                        <a-select-option v-for="config in sttConfigs" :key="config.sttId" :value="config.sttId">
                          {{ config.sttName }}
                        </a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                </a-row>
                
                <!-- VAD高级设置 -->
                <a-collapse :bordered="false" style="background: transparent; margin-bottom: 24px;" @change="handleVadCollapseChange">
                  <a-collapse-panel header="语音识别高级设置 (VAD参数)" key="1">
                    <a-row :gutter="16">
                      <a-col :xl="6" :lg="12" :xs="24">
                        <a-form-item label="语音阈值" :labelCol="{ span: 10 }" :wrapperCol="{ span: 14 }">
                          <a-input-number v-decorator="[
                            'vadSpeechTh',
                            { initialValue: defaultVadSettings.vadSpeechTh }
                          ]" :min="0" :max="1" :step="0.1" style="width: 100%" />
                        </a-form-item>
                      </a-col>
                      <a-col :xl="6" :lg="12" :xs="24">
                        <a-form-item label="静音阈值" :labelCol="{ span: 10 }" :wrapperCol="{ span: 14 }">
                          <a-input-number v-decorator="[
                            'vadSilenceTh',
                            { initialValue: defaultVadSettings.vadSilenceTh }
                          ]" :min="0" :max="1" :step="0.1" style="width: 100%" />
                        </a-form-item>
                      </a-col>
                      <a-col :xl="6" :lg="12" :xs="24">
                        <a-form-item label="能量阈值" :labelCol="{ span: 10 }" :wrapperCol="{ span: 14 }">
                          <a-input-number v-decorator="[
                            'vadEnergyTh',
                            { initialValue: defaultVadSettings.vadEnergyTh }
                          ]" :min="0" :max="1" :step="0.01" style="width: 100%" />
                        </a-form-item>
                      </a-col>
                      <a-col :xl="6" :lg="12" :xs="24">
                        <a-form-item label="静音时长" :labelCol="{ span: 10 }" :wrapperCol="{ span: 14 }">
                          <a-input-number v-decorator="[
                            'vadSilenceMs',
                            { initialValue: defaultVadSettings.vadMinSilenceMs }
                          ]" :min="0" :max="5000" :step="100" style="width: 100%" />
                        </a-form-item>
                      </a-col>
                    </a-row>
                  </a-collapse-panel>
                </a-collapse>

                <!-- 语音合成设置区域 -->
                <a-divider orientation="left">语音合成设置</a-divider>
                
                <a-row :gutter="20">
                  <!-- 语音提供商选择 -->
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item label="语音提供商">
                      <a-select v-decorator="['ttsProvider', { initialValue: 'edge' }]" placeholder="请选择语音提供商"
                        @change="handleProviderChange">
                        <a-select-option value="edge">微软Edge</a-select-option>
                        <a-select-option value="aliyun">阿里云</a-select-option>
                        <a-select-option value="volcengine">火山引擎（豆包）</a-select-option>
                        <a-select-option value="xfyun">讯飞云</a-select-option>
                        <a-select-option value="minimax">Minimax</a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                  
                  <!-- TTS配置选择 -->
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item label="TTS配置">
                      <a-select v-decorator="[
                        'ttsId',
                        {
                          rules: [
                            { required: true, message: '请选择TTS配置' },
                          ],
                        },
                      ]" placeholder="请选择TTS配置" @change="handleTtsConfigChange" :loading="ttsConfigLoading">
                        <!-- 为Edge提供默认配置 -->
                        <a-select-option v-if="selectedProvider === 'edge'" value="edge_default">
                          默认配置
                        </a-select-option>
                        <!-- 为其他提供商显示动态配置 -->
                        <a-select-option v-for="config in ttsConfigs" :key="config.configId" :value="config.configId">
                          {{ config.configName }}
                        </a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                  
                  <!-- 语音性别选择 -->
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item label="语音性别">
                      <a-select v-decorator="['gender', { initialValue: '' }]" placeholder="请选择语音性别"
                        @change="handleGenderChange">
                        <a-select-option value="">不限</a-select-option>
                        <a-select-option value="male">男声</a-select-option>
                        <a-select-option value="female">女声</a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                  
                  <!-- 语音名称选择 -->
                  <a-col :xl="6" :lg="12" :xs="24">
                    <a-form-item label="语音名称">
                      <a-select v-decorator="[
                        'voiceName',
                        {
                          initialValue: defaultVoiceName,
                          rules: [
                            { required: true, message: '请选择语音名称' },
                          ],
                        },
                      ]" placeholder="请选择语音名称" :loading="voiceLoading">
                        <a-select-option v-for="voice in filteredVoices" :key="voice.value"
                          :value="voice.value">
                          {{ voice.label }}
                        </a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                </a-row>
                
                <!-- 语音测试区域 -->
                <a-row :gutter="20">
                  <a-col :xl="12" :lg="12" :xs="24">
                    <a-form-item label="语音测试">
                      <a-input-search v-model="testText" placeholder="请输入要测试的文本" enter-button="测试"
                        :loading="audioTesting" @search="testVoice" />
                    </a-form-item>
                  </a-col>
                </a-row>

                <!-- 音频播放器 -->
                <a-card v-if="audioUrl" size="small" :bordered="false" style="margin-bottom: 24px;">
                  <AudioPlayer :audioUrl="audioUrl" :autoPlay="true" />
                </a-card>

                <!-- 角色提示词(Prompt) -->
                <a-divider orientation="left">角色提示词(Prompt)</a-divider>

                <!-- 添加智能体提示信息 -->
                <a-alert v-if="selectedModelType === 'agent'" 
                  message="智能体模式下使用智能体自带的提示词，无需额外设置"
                  description="智能体已包含预设的提示词和知识库，将自动使用智能体的描述作为角色提示词"
                  type="info" 
                  show-icon
                  style="margin-bottom: 16px" />

                <template v-else>
                  <!-- 提示词编辑模式选择 -->
                  <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
                    <a-space>
                      <a-radio-group v-model="promptEditorMode" button-style="solid" @change="handlePromptModeChange">
                        <a-radio-button value="template">使用模板</a-radio-button>
                        <a-radio-button value="custom">自定义</a-radio-button>
                      </a-radio-group>

                      <template v-if="promptEditorMode === 'template'">
                        <a-select style="width: 200px" placeholder="选择模板" v-model="selectedTemplateId"
                          @change="handleTemplateChange" :loading="templatesLoading">
                          <a-select-option v-for="template in promptTemplates" :key="template.templateId"
                            :value="template.templateId">
                            {{ template.templateName }}
                            <a-tag v-if="template.isDefault == 1" color="green" size="small">默认</a-tag>
                          </a-select-option>
                        </a-select>
                      </template>
                    </a-space>

                    <!-- 模板管理按钮 -->
                    <a-button type="primary" @click="goToTemplateManager">
                      <a-icon type="snippets" /> 模板管理
                    </a-button>
                  </div>
                </template>

                <!-- 提示词编辑区域 -->
                <a-form-item>
                  <a-textarea v-decorator="[
                    'roleDesc',
                    {
                      rules: [],
                    },
                  ]" :disabled="selectedModelType === 'agent'" :rows="10" placeholder="请输入角色提示词，描述角色的特点、知识背景和行为方式等" />
                </a-form-item>
                <!-- 表单操作按钮 -->
                <a-form-item>
                  <a-button type="primary" html-type="submit" :loading="submitLoading">
                    {{ editingRoleId ? "更新角色" : "创建角色" }}
                  </a-button>
                  <a-button style="margin-left: 8px" @click="cancel">
                    取消
                  </a-button>
                </a-form-item>
              </a-form>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </div>
    </a-layout-content>
  </a-layout>
</template>
<script>
import axios from '@/services/axios';
import api from '@/services/api';
import mixin from '@/mixins/index';
import AudioPlayer from '@/components/AudioPlayer.vue';

// 模型类型常量
const MODEL_TYPE = {
  LLM: 'llm',
  AGENT: 'agent',
  UNKNOWN: 'unknown'
};

// 提供商常量
const PROVIDER = {
  COZE: 'coze',
  DIFY: 'dify',
  OTHER: 'other'
};
import { getResourceUrl } from '@/services/axios';

export default {
  components: { AudioPlayer },
  mixins: [mixin],
  data() {
    return {
      // 角色列表相关
      roleItems: [],
      loading: false,
      submitLoading: false,
      activeTabKey: '1',
      editingRoleId: null,
      editingRoleDesc: '',
      
      // 查询相关
      query: {},
      queryFilter: [
        { label: '角色名称', index: 'roleName' },
      ],
      
      // 表格列定义
      columns: [
        {
          title: '头像',
          dataIndex: 'avatarUrl',
          width: 80,
          align: 'center',
          scopedSlots: { customRender: 'avatar' },
        },
        {
          title: '角色名称',
          dataIndex: 'roleName',
          width: 120,
          align: 'center',
        },
        {
          title: '角色描述',
          dataIndex: 'roleDesc',
          scopedSlots: { customRender: 'roleDesc' },
          width: 200,
          align: 'center',
        },
        {
          title: '音色',
          dataIndex: 'voiceName',
          scopedSlots: { customRender: 'voiceName' },
          width: 150,
          align: 'center',
        },
        {
          title: '模型',
          dataIndex: 'modelName',
          scopedSlots: { customRender: 'modelName' },
          width: 150,
          align: 'center',
        },
        {
          title: '语音识别',
          dataIndex: 'sttName',
          scopedSlots: { customRender: 'sttName' },
          width: 150,
          align: 'center',
        },
        {
          title: '设备数量',
          dataIndex: 'totalDevice',
          scopedSlots: { customRender: 'totalDevice' },
          width: 80,
          align: 'center',
        },
        {
          title: '默认角色',
          dataIndex: 'isDefault',
          scopedSlots: { customRender: 'isDefault' },
          width: 100,
          align: 'center',
        },
        {
          title: '操作',
          dataIndex: 'operation',
          scopedSlots: { customRender: 'operation' },
          width: 180,
          align: 'center',
          fixed: 'right',
        },
      ],
      
      // 表单相关
      roleForm: this.$form.createForm(this),
      promptEditorMode: 'custom',
      selectedTemplateId: null,
      promptTemplates: [],
      templatesLoading: false,
      
      // 语音合成相关
      selectedProvider: 'edge',
      selectedGender: '',
      selectedTtsId: 'edge_default',
      ttsConfigs: [],
      ttsConfigLoading: false,
      voiceLoading: false,
      edgeVoices: [],
      aliyunVoices: [],
      volcengineVoices: [],
      xfyunVoices: [],
      minimaxVoices: [],
      testText: '你好，我是小智，很高兴为您服务',
      audioUrl: '',
      audioTesting: false,
      
      // 模型相关
      modelLoading: false,
      selectedModelType: 'llm',
      selectedModelProvider: '',
      modelItems: [],
      agentItems: [],
      cozeAgents: [],
      difyAgents: [],
      llmProviders: [],
      providerMap: {},
      
      // 语音识别相关
      sttConfigLoading: false,
      sttConfigs: [],
      sttItems: [], // 添加这个字段用于存储STT配置
      
      // VAD默认设置
      defaultVadSettings: {
        vadSpeechTh: 0.5,
        vadSilenceTh: 0.3,
        vadEnergyTh: 0.01,
        vadMinSilenceMs: 1200
      },
      
      // 默认配置
      defaultRole: null,
      defaultModelConfig: null,
      defaultSttConfig: null,

      // 头像相关
      avatarUrl: '',
      avatarLoading: false,
      avatarFile: null,
      
      // 待设置的VAD参数（用于编辑时延迟设置）
      pendingVadValues: null,
      pendingModelValues: null,
    };
  },
  
  computed: {
    // 根据性别筛选语音列表
    filteredVoices() {
      let voices = [];
      
      // 根据选择的提供商获取对应的语音列表
      if (this.selectedProvider === 'edge') {
        voices = this.edgeVoices;
      } else if (this.selectedProvider === 'aliyun') {
        voices = this.aliyunVoices;
      } else if (this.selectedProvider === 'volcengine') {
        voices = this.volcengineVoices;
      } else if (this.selectedProvider === 'xfyun') {
        voices = this.xfyunVoices;
      } else if (this.selectedProvider === 'minimax') {
        voices = this.minimaxVoices;
      }
      
      // 如果选择了性别，则按性别筛选
      if (this.selectedGender) {
        voices = voices.filter(voice => voice.gender === this.selectedGender);
      }
      
      return voices;
    },
    
    // 默认语音名称
    defaultVoiceName() {
      if (this.filteredVoices && this.filteredVoices.length > 0) {
        return this.filteredVoices[0].value;
      }
      return '';
    },
    
    // 根据模型类型和提供商筛选模型
    filteredModels() {
      if (!this.selectedModelProvider) return [];
      return this.providerMap[this.selectedModelProvider] || [];
    },
    
    // 根据提供商筛选智能体
    filteredAgents() {
      if (this.selectedModelProvider === PROVIDER.COZE) {
        return this.cozeAgents;
      } else if (this.selectedModelProvider === PROVIDER.DIFY) {
        return this.difyAgents;
      }
      return [];
    }
  },
  
  mounted() {
    this.loading = true;
    // 加载基础数据
    Promise.all([
      // 加载智能体数据
      this.loadAgents(),
      this.loadConfig(),
      this.getDefaultRole()
    ]).then(() => {
      this.getData();
      this.loadTemplates();
      this.loadAllVoiceData();
    });
  },
  
  methods: {

    // 获取默认角色
    getDefaultRole() {
      axios.get({
        url: api.role.query,
        data: {
          isDefault: 1
        }
      }).then(res => {
        if (res.code === 200 && res.data.list && res.data.list.length > 0) {
          this.defaultRole = res.data.list[0];
          // 确保模型类型和提供商信息已经确定
          if (this.defaultRole.modelId && (!this.defaultRole.modelType || !this.defaultRole.modelProvider)) {
            this.determineModelType(this.defaultRole);
          }
        }
      }).catch(() => {
        console.error('获取默认角色失败');
      });
    },

    // 标签页切换处理
    handleTabChange(key) {
      this.activeTabKey = key;
      if (key === '1') {
        this.getData();
      } else if (key === '2') {
        this.resetForm();
      }
    },
    
    // 加载所有语音数据
    loadAllVoiceData() {
      this.loadEdgeVoices();
      this.loadAliyunVoices();
      this.loadVolcengineVoices();
      this.loadXfyunVoices();
      this.loadMinimaxVoices();
    },
    
    // 加载配置数据（模型和语音识别）
    loadConfig() {
      this.modelLoading = true;
      this.sttConfigLoading = true;
      
      axios.get({ url: api.config.query })
        .then(res => {
          if (res.code === 200) {
            // 初始化提供商映射
            this.providerMap = {};
            this.modelItems = [];
            this.sttConfigs = [];
            this.sttItems = []; // 清空STT项
            
            // 添加默认的本地语音识别
            const voskItem = {
              sttId: -1,
              sttName: "Vosk本地识别",
              sttDesc: "默认Vosk本地语音识别模型",
            };
            this.sttConfigs.push(voskItem);
            this.sttItems.push(voskItem);
            
            // 处理配置数据
            res.data.list.forEach(item => {
              if (item.configType === "llm") {
                this.processLlmModel(item);
                if (item.isDefault == 1) {
                  this.defaultModelConfig = item;
                }
              } else if (item.configType === "stt") {
                this.processSttModel(item);
                if (item.isDefault == 1) {
                  this.defaultSttConfig = item;
                }
              }
            });
            
            // 提取LLM提供商列表
            this.llmProviders = Object.keys(this.providerMap);
            
          } else {
            this.showError(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.modelLoading = false;
          this.sttConfigLoading = false;
        });
    },
    
    // 处理LLM模型数据
    processLlmModel(item) {
      // 标准化字段
      item.modelId = item.configId;
      item.modelName = item.configName;
      item.modelDesc = item.configDesc;
      this.modelItems.push(item);

      // 按提供商分组
      const provider = item.provider || PROVIDER.OTHER;
      if (!this.providerMap[provider]) {
        this.providerMap[provider] = [];
      }
      this.providerMap[provider].push(item);
    },
    
    // 处理STT模型数据
    processSttModel(item) {
      item.sttId = item.configId;
      item.sttName = item.configName;
      item.sttDesc = item.configDesc;
      this.sttConfigs.push(item);
      this.sttItems.push(item); // 同时添加到sttItems
    },
    
    // 加载智能体数据
    loadAgents() {
      // 清空现有智能体列表
      this.agentItems = [];
      this.cozeAgents = [];
      this.difyAgents = [];
      
      // 并行请求两个提供商的智能体
      return Promise.all([
        this.getProviderAgents(PROVIDER.COZE),
        this.getProviderAgents(PROVIDER.DIFY)
      ]);
    },
    
    // 获取指定提供商的智能体
    getProviderAgents(provider) {
      this.modelLoading = true;
      return axios
        .get({
          url: api.agent.query,
          data: { provider },
        })
        .then((res) => {
          if (res.code === 200) {
            // 处理智能体数据
            res.data.list.forEach((item) => {
              this.processAgentItem(item, provider);
            });
          } else {
            this.$message.error(res.message);
          }
        })
        .catch(() => {
          this.showError();
        })
        .finally(() => {
          this.modelLoading = false;
        });
    },
    
    // 处理智能体数据
    processAgentItem(item, provider) {
      // 标准化字段
      item.modelId = item.configId;
      item.provider = provider;
      item.modelName = item.agentName;
      item.modelDesc = item.agentDesc;
      item.modelType = MODEL_TYPE.AGENT;
      
      // 添加到对应提供商的列表
      if (provider === PROVIDER.COZE) {
        this.cozeAgents.push(item);
      } else if (provider === PROVIDER.DIFY) {
        this.difyAgents.push(item);
      }
      
      // 添加到总智能体列表
      this.agentItems.push(item);
    },
    
    // 加载TTS配置
    loadTtsConfigs(provider) {
      this.ttsConfigLoading = true;

      axios
        .get({
          url: api.config.query,
          data: {
            configType: 'tts',
            provider: provider
          }
        })
        .then(res => {
          if (res.code === 200) {
            this.ttsConfigs = res.data.list;

            // 如果不在编辑模式且有配置项，默认选择第一个
            if (!this.editingRoleId && this.ttsConfigs.length > 0) {
              this.selectedTtsId = this.ttsConfigs[0].configId;
              this.$nextTick(() => {
                this.roleForm.setFieldsValue({
                  ttsId: this.selectedTtsId
                });
              });
            }
          } else {
            this.showError(res.message);
          }
        })
        .catch(() => {
          this.$message.error('加载TTS配置失败，请稍后再试');
        })
        .finally(() => {
          this.ttsConfigLoading = false;
        });
    },

    // 处理性别选择变化
    handleGenderChange(value) {
      this.selectedGender = value;

      // 当性别变化时，设置语音名称为新的默认值（该性别的第一个语音）
      this.$nextTick(() => {
        if (this.filteredVoices && this.filteredVoices.length > 0) {
          this.roleForm.setFieldsValue({
            voiceName: this.filteredVoices[0].value
          });
        } else {
          this.roleForm.setFieldsValue({
            voiceName: undefined
          });
        }
      });
    },

    // 获取角色列表
    getData() {
      this.loading = true;
      axios
        .get({
          url: api.role.query,
          data: {
            start: this.pagination.page,
            limit: this.pagination.pageSize,
            ...this.query
          }
        })
        .then(res => {
          if (res.code === 200) {
            // 处理角色数据，确定模型类型和提供商
            this.roleItems = res.data.list.map(role => {
              this.determineModelType(role);
              if (role.sttId == null) {
                role.sttId = -1
              }
              return role;
            });
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
    
    // 确定模型类型（LLM或智能体）
    determineModelType(role) {
      if (!role.modelId) {
        // 确保没有modelId的角色也有基本属性
        this.resetModelInfo(role);
        return;
      }

      // 转换为数字进行比较（确保类型一致）
      const modelId = Number(role.modelId);
      
      // 如果已经有modelProvider，优先使用它来确定模型类型
      if (role.modelProvider) {
        // 检查是否是智能体提供商
        if (role.modelProvider === PROVIDER.COZE) {
          // 查找Coze智能体
          if (this.findAndApplyAgentModel(role, modelId, this.cozeAgents, PROVIDER.COZE)) return;
        } else if (role.modelProvider === PROVIDER.DIFY) {
          // 查找Dify智能体
          if (this.findAndApplyAgentModel(role, modelId, this.difyAgents, PROVIDER.DIFY)) return;
        } else {
          // 查找LLM模型（按提供商筛选）
          const providerModels = this.providerMap[role.modelProvider] || [];
          const model = providerModels.find(m => Number(m.configId) === modelId);
          if (model) {
            role.modelType = MODEL_TYPE.LLM;
            role.modelName = model.configName || '未知模型';
            role.modelDesc = model.configDesc || '';
            return;
          }
        }
      }
      
      // 如果没有提供商信息或者根据提供商没找到，则尝试在所有模型中查找
      // 按优先级检查模型类型
      if (this.findAndApplyAgentModel(role, modelId, this.cozeAgents, PROVIDER.COZE)) return;
      if (this.findAndApplyAgentModel(role, modelId, this.difyAgents, PROVIDER.DIFY)) return;
      if (this.findAndApplyLlmModel(role, modelId)) return;
      
      // 未找到匹配的模型，设置为未知
      role.modelType = MODEL_TYPE.UNKNOWN;
      role.modelName = role.modelName || `未知模型(ID:${modelId})`;
      role.modelDesc = '';
      role.modelProvider = role.modelProvider || '';
    },
    
    // 重置模型信息
    resetModelInfo(role) {
      role.modelType = '';
      role.modelName = '';
      role.modelDesc = '';
      role.modelProvider = '';
    },
    
    // 查找并应用智能体模型
    findAndApplyAgentModel(role, modelId, agentList, provider) {
      const agent = agentList.find(a => Number(a.configId) === modelId);
      if (agent) {
        role.modelType = MODEL_TYPE.AGENT;
        role.modelName = agent.agentName || '未知智能体';
        role.modelDesc = agent.agentDesc || '';
        role.modelProvider = provider;
        return true;
      }
      return false;
    },
    
    // 查找并应用LLM模型
    findAndApplyLlmModel(role, modelId) {
      const model = this.modelItems.find(m => Number(m.configId) === modelId);
      if (model) {
        role.modelType = MODEL_TYPE.LLM;
        role.modelName = model.configName || '未知模型';
        role.modelDesc = model.configDesc || '';
        role.modelProvider = model.provider || '';
        return true;
      }
      return false;
    },

    // 加载Edge语音列表
    loadEdgeVoices() {
      this.voiceLoading = true;
      
      fetch('/static/assets/edgeVoicesList.json')
        .then(response => {
          if (!response.ok) {
            throw new Error('加载Edge语音列表失败');
          }
          return response.json();
        })
        .then(data => {
          // 提取中文语音列表
          const voices = data
            .filter(voice => voice.Locale.includes('zh'))
            .sort((a, b) => a.Locale.localeCompare(b.Locale))
            .map(voice => {
              // 从ShortName中提取名称部分 (如从"zh-TW-HsiaoYuNeural"提取"HsiaoYu")
              const nameParts = voice.ShortName.split('-');
              let name = nameParts[2];

              // 移除Neural后缀
              if (name.endsWith('Neural')) {
                name = name.substring(0, name.length - 6);
              }

              // 获取区域代码
              const locale = voice.Locale;
              return {
                label: `${name} (${locale})`,
                value: voice.ShortName,
                gender: voice.Gender.toLowerCase(),
                provider: 'edge'
              };
            });

          // 保存语音列表
          this.edgeVoices = voices;

          // 加载完语音列表后，如果当前选择的是Edge，设置默认语音
          this.$nextTick(() => {
            if (this.selectedProvider === 'edge' && this.edgeVoices.length > 0 && this.activeTabKey === '2') {
              this.roleForm.setFieldsValue({
                voiceName: this.defaultVoiceName
              });
            }
          });
        })
        .catch(error => {
          this.$message.error('加载Edge语音列表失败，请刷新页面重试');
        })
        .finally(() => {
          this.voiceLoading = false;
        });
    },

    // 加载阿里云语音列表 - 从本地文件加载
    loadAliyunVoices() {
      this.voiceLoading = true;
      
      // 直接从本地文件加载阿里云语音列表
      fetch('/static/assets/aliyunVoicesList.json')
        .then(response => {
          if (!response.ok) {
            throw new Error('加载阿里云语音列表失败');
          }
          return response.json();
        })
        .then(voices => {
          // 保存语音列表
          this.aliyunVoices = voices;

          // 加载完语音列表后，如果当前选择的是阿里云，设置默认语音
          this.$nextTick(() => {
            if (
              this.selectedProvider === "aliyun" &&
              this.aliyunVoices.length > 0 &&
              this.activeTabKey === "2"
            ) {
              this.roleForm.setFieldsValue({
                voiceName: this.defaultVoiceName,
              });
            }
          });
        })
        .catch(error => {
          this.$message.error('加载阿里云语音列表失败，请确认文件是否存在');
        })
        .finally(() => {
          this.voiceLoading = false;
        });
    },

    // 加载火山引擎语音列表 - 从本地文件加载
    loadVolcengineVoices() {
      this.voiceLoading = true;
      
      // 直接从本地文件加载火山引擎语音列表
      fetch('/static/assets/volcengineVoicesList.json')
        .then(response => {
          if (!response.ok) {
            throw new Error('加载火山引擎语音列表失败');
          }
          return response.json();
        })
        .then(voices => {
          // 保存语音列表
          this.volcengineVoices = voices;

          // 加载完语音列表后，如果当前选择的是火山引擎，设置默认语音
          this.$nextTick(() => {
            if (
              this.selectedProvider === "volcengine" &&
              this.volcengineVoices.length > 0 &&
              this.activeTabKey === "2"
            ) {
              this.roleForm.setFieldsValue({
                voiceName: this.defaultVoiceName,
              });
            }
          });
        })
        .catch(error => {
          this.$message.error('加载火山引擎语音列表失败，请确认文件是否存在');
        })
        .finally(() => {
          this.voiceLoading = false;
        });
    },
    
    // 加载讯飞云语音列表 - 从本地文件加载
    loadXfyunVoices() {
      this.voiceLoading = true;

      // 直接从本地文件加载火山引擎语音列表
      fetch('/static/assets/xfyunVoicesList.json')
        .then(response => {
          if (!response.ok) {
            throw new Error('加载讯飞云语音列表失败');
          }
          return response.json();
        })
        .then(voices => {
          // 保存语音列表
          this.xfyunVoices = voices;

          // 加载完语音列表后，设置默认语音
          this.$nextTick(() => {
            if (
              this.selectedProvider === "xfyun" &&
              this.xfyunVoices.length > 0 &&
              this.activeTabKey === "2"
            ) {
              this.roleForm.setFieldsValue({
                voiceName: this.defaultVoiceName,
              });
            }
          });
        })
        .catch(error => {
          this.$message.error('加载讯飞云语音列表失败，请确认文件是否存在');
        })
        .finally(() => {
          this.voiceLoading = false;
        });
    },
    
    // 加载Minimax语音列表 - 从本地文件加载
    loadMinimaxVoices() {
      this.voiceLoading = true;

      // 直接从本地文件加载火山引擎语音列表
      fetch('/static/assets/minimaxVoicesList.json')
        .then(response => {
          if (!response.ok) {
            throw new Error('加载Minimax语音列表失败');
          }
          return response.json();
        })
        .then(voices => {
          // 保存语音列表
          this.minimaxVoices = voices;

          // 加载完语音列表后，设置默认语音
          this.$nextTick(() => {
            if (
              this.selectedProvider === "minimax" &&
              this.minimaxVoices.length > 0 &&
              this.activeTabKey === "2"
            ) {
              this.roleForm.setFieldsValue({
                voiceName: this.defaultVoiceName,
              });
            }
          });
        })
        .catch(error => {
          this.$message.error('加载Minimax语音列表失败，请确认文件是否存在');
        })
        .finally(() => {
          this.voiceLoading = false;
        });
    },
    
    // 提交表单
    handleSubmit(e) {
      e.preventDefault();
      this.roleForm.validateFields((err, values) => {
        if (!err) {
          this.submitLoading = true;

          // 添加语音提供商信息
          const formData = {
            ...values,
            avatar: this.avatarUrl,
            // 将开关的布尔值转换为数字（0或1）
            isDefault: values.isDefault ? 1 : 0
          };

          // 处理ttsId
          // 如果是Edge，使用特殊标记
          if (values.ttsProvider === "edge") {
            formData.ttsId = -1;
          }
          // 其他提供商使用选择的ttsId

          const url = this.editingRoleId ? api.role.update : api.role.add;

          axios
            .post({
              url,
              data: {
                roleId: this.editingRoleId,
                ...formData
              }
            })
            .then(res => {
              if (res.code === 200) {
                this.$message.success(
                  this.editingRoleId ? '更新成功' : '创建成功'
                );
                this.resetForm();
                this.getData();
                // 成功后切换到角色列表页
                this.activeTabKey = '1';
              } else {
                this.showError(res.message);
              }
            })
            .catch(() => {
              this.showError();
            })
            .finally(() => {
              this.submitLoading = false;
            });
        }
      });
    },

    // 编辑角色
    edit(record) {
      this.editingRoleId = record.roleId;
      this.editingRoleDesc = record.roleDesc;
      this.avatarUrl = record.avatar || ''; // 设置当前头像
      this.avatarFile = null; // 清空文件对象，因为是编辑现有头像
      // 切换到创建角色标签页
      this.activeTabKey = '2';

      // 首先确保模型类型和提供商信息已经确定
      if (record.modelId && (!record.modelType || !record.modelProvider)) {
        this.determineModelType(record);
      }

      this.$nextTick(() => {
        const { roleForm } = this;

        // 设置语音提供商
        this.selectedProvider = record.ttsProvider || 'edge';
        
        // 根据提供商加载TTS配置
        if (this.selectedProvider === "edge") {
          // Edge使用默认配置
          this.ttsConfigs = [];
          this.selectedTtsId = "edge_default";
        } else {
          // 加载TTS配置并设置选中的TTS配置ID
          this.loadTtsConfigs(this.selectedProvider);
          this.selectedTtsId = record.ttsId;
        }
        
        // 设置当前选择的性别，以便正确筛选语音
        this.selectedGender = record.gender || '';
        
        // 设置模型类型和提供商
        this.selectedModelType = record.modelType || MODEL_TYPE.LLM;
        this.selectedModelProvider = record.modelProvider || '';

        // 准备折叠面板内的VAD参数值（延迟到用户展开时设置）
        this.pendingVadValues = {
          vadSpeechTh: record.vadSpeechTh !== null && record.vadSpeechTh !== undefined ? record.vadSpeechTh : this.defaultVadSettings.vadSpeechTh,
          vadSilenceTh: record.vadSilenceTh !== null && record.vadSilenceTh !== undefined ? record.vadSilenceTh : this.defaultVadSettings.vadSilenceTh,
          vadEnergyTh: record.vadEnergyTh !== null && record.vadEnergyTh !== undefined ? record.vadEnergyTh : this.defaultVadSettings.vadEnergyTh,
          vadSilenceMs: record.vadSilenceMs !== null && record.vadSilenceMs !== undefined ? record.vadSilenceMs : this.defaultVadSettings.vadMinSilenceMs,
        };
        
        // 准备折叠面板内的模型参数值（延迟到用户展开时设置）
        this.pendingModelValues = {
          temperature: record.temperature !== null && record.temperature !== undefined ? record.temperature : 0.7,
          topP: record.topP !== null && record.topP !== undefined ? record.topP : 0.9,
        };

        // 设置表单基础值，将isDefault从数字转为布尔值
        roleForm.setFieldsValue({
          roleName: record.roleName,
          roleDesc: record.roleDesc,
          ttsProvider: this.selectedProvider,
          gender: this.selectedGender,
          isDefault: record.isDefault == 1,
          
          // 模型相关
          modelType: this.selectedModelType,
          modelProvider: this.selectedModelProvider,
          modelId: record.modelId,
          
          // 语音识别
          sttId: record.sttId,
        });
        
        // 在所有数据加载完成后，设置TTS和语音名称
        // 需要延迟设置，确保相关语音列表已加载
        setTimeout(() => {
          roleForm.setFieldsValue({
            ttsId: this.selectedTtsId,
            voiceName: record.voiceName
          });
        }, 500);
      });
    },

    // 删除/禁用角色
    update(roleId, state) {
      this.loading = true;
      axios
        .post({
          url: api.role.update,
          data: {
            roleId: roleId,
            state: state,
          },
        })
        .then((res) => {
          if (res.code === 200) {
            this.$message.success("操作成功");
            this.getData();
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
    
    // 设置为默认角色
    setAsDefault(record) {
      this.loading = true;
      axios
        .post({
          url: api.role.update,
          data: {
            roleId: record.roleId,
            isDefault: 1
          }
        })
        .then(res => {
          if (res.code === 200) {
            this.$message.success('已设置为默认角色');
            this.getData();
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

    // 重置表单
    resetForm() {
      this.roleForm.resetFields();
      this.editingRoleId = null;
      this.promptEditorMode = 'custom';
      this.audioUrl = '';
      this.avatarUrl = ''; // 重置头像
      this.avatarFile = null; // 清空文件对象
      // 清空待设置的折叠面板值
      this.pendingVadValues = null;
      this.pendingModelValues = null;
      // 应用默认值
      this.applyDefaultValues();
    },

    
    // 取消
    cancel() {
      this.resetForm()
      this.handleTabChange('1')
    },
    
    // 应用默认值
    applyDefaultValues() {
      // 基本默认值
      let defaults = {
        ttsProvider: 'edge',
        gender: '',
        ttsId: 'edge_default',
        isDefault: false,
        modelType: 'llm',
        
        // VAD默认参数
        vadSpeechTh: this.defaultVadSettings.vadSpeechTh,
        vadSilenceTh: this.defaultVadSettings.vadSilenceTh,
        vadEnergyTh: this.defaultVadSettings.vadEnergyTh,
        vadSilenceMs: this.defaultVadSettings.vadMinSilenceMs,
        
        // 模型默认参数
        temperature: 0.7,
        topP: 0.9,
      };
      
      // 如果有默认角色，使用默认角色的设置
      if (this.defaultRole) {
        // 设置提供商和模型相关信息
        this.selectedProvider = this.defaultRole.ttsProvider || 'edge';
        this.selectedGender = this.defaultRole.gender || '';
        this.selectedModelType = this.defaultRole.modelType || MODEL_TYPE.LLM;
        this.selectedModelProvider = this.defaultRole.modelProvider || '';
        
        // 更新默认值
        defaults = {
          ...defaults,
          ttsProvider: this.selectedProvider,
          gender: this.selectedGender,
          modelType: this.selectedModelType,
          modelProvider: this.selectedModelProvider,
          modelId: this.defaultRole.modelId,
          sttId: this.defaultRole.sttId || -1,
          
          // VAD参数
          vadSpeechTh: this.defaultRole.vadSpeechTh || defaults.vadSpeechTh,
          vadSilenceTh: this.defaultRole.vadSilenceTh || defaults.vadSilenceTh,
          vadEnergyTh: this.defaultRole.vadEnergyTh || defaults.vadEnergyTh,
          vadSilenceMs: this.defaultRole.vadSilenceMs || defaults.vadSilenceMs,
          
          // 模型参数
          temperature: this.defaultRole.temperature || defaults.temperature,
          topP: this.defaultRole.topP || defaults.topP,
        };
        
        // 如果是Edge，使用特殊标记
        if (this.selectedProvider === "edge") {
          defaults.ttsId = "edge_default";
          this.ttsConfigs = [];
        } else {
          // 加载TTS配置
          this.loadTtsConfigs(this.selectedProvider);
          defaults.ttsId = this.defaultRole.ttsId;
        }
      } else if (this.defaultModelConfig) {
        // 如果没有默认角色但有默认模型，使用默认模型
        this.selectedModelType = MODEL_TYPE.LLM;
        this.selectedModelProvider = this.defaultModelConfig.provider || '';
        
        defaults.modelType = MODEL_TYPE.LLM;
        defaults.modelProvider = this.defaultModelConfig.provider;
        defaults.modelId = this.defaultModelConfig.configId;
      }
      
      // 设置表单值
      this.$nextTick(() => {
        this.roleForm.setFieldsValue(defaults);
        
        // 延迟设置语音名称，确保语音列表已加载
        setTimeout(() => {
          // 如果有默认角色，使用默认角色的语音
          if (this.defaultRole && this.defaultRole.voiceName) {
            this.roleForm.setFieldsValue({
              voiceName: this.defaultRole.voiceName
            });
          } else {
            // 否则使用当前筛选后的第一个语音
            this.roleForm.setFieldsValue({
              voiceName: this.defaultVoiceName
            });
          }
        }, 500);
      });
      
      // 如果有默认模板，应用默认模板
      if (this.promptTemplates && this.promptTemplates.length > 0) {
        const defaultTemplate = this.promptTemplates.find(t => t.isDefault == 1);
        if (defaultTemplate) {
          this.selectedTemplateId = defaultTemplate.templateId;
          this.promptEditorMode = 'template';

          this.$nextTick(() => {
            this.roleForm.setFieldsValue({
              roleDesc: defaultTemplate.templateContent
            });
          });
        }
      }
    },

    // 加载提示词模板列表
    loadTemplates() {
      this.templatesLoading = true;

      axios.get({
        url: api.template.query,
        data: {}
      })
        .then(res => {
          if (res.code === 200) {
            this.promptTemplates = res.data.list;
            // 如果有默认模板，自动选择
            const defaultTemplate = this.promptTemplates.find(t => t.isDefault == 1);
            if (defaultTemplate) {
              this.selectedTemplateId = defaultTemplate.templateId;
              if (this.promptEditorMode === 'template') {
                this.roleForm.setFieldsValue({
                  roleDesc: defaultTemplate.templateContent
                });
              }
            }
          } else {
            this.showError(res.message);
          }
        })
        .catch(() => {
          this.$message.error("获取模板列表失败");
        })
        .finally(() => {
          this.templatesLoading = false;
        });
    },

    // 测试语音
    testVoice() {
      if (!this.testText.trim()) {
        this.$message.warning('请输入测试文本');
        return;
      }

      this.roleForm.validateFields(['voiceName', 'ttsId', 'ttsProvider'], (err, values) => {
        if (err) {
          return;
        }
        
        this.audioTesting = true;
        
        // 构建请求参数
        const requestData = {
          voiceName: values.voiceName,
          provider: values.ttsProvider,
          message: this.testText
        };

        // 普通TTS配置
        // 如果是Edge，使用特殊标记
        if (values.ttsProvider === "edge") {
          requestData.ttsId = -1;
        } else {
          requestData.ttsId = values.ttsId;
        }

        axios
          .get({
            url: api.role.testVoice,
            data: requestData
          }).then(res => {
            if (res.code === 200) {
              this.audioUrl = res.data;
            } else {
              this.showError(res.message);
            }
          }).catch((e) => {
            this.$message.error('语音合成失败，请稍后再试');
          }).finally(() => {
            this.audioTesting = false;
          });
      });
    },
    
    // 处理模型类型变更
    handleModelTypeChange(e) {
      this.selectedModelType = e.target.value;
      this.selectedModelProvider = '';
      
      // 清空模型选择
      this.$nextTick(() => {
        this.roleForm.setFieldsValue({
          modelProvider: undefined,
          modelId: undefined,
          roleDesc: this.selectedModelType === MODEL_TYPE.LLM ? this.editingRoleDesc : '' // 如果切换回 LLM 恢复原始 roleDesc
        });
      });
    },
    
    // 处理模型提供商变更
    handleProviderChangeForModel(value) {
      this.selectedModelProvider = value;
      
      // 清空模型选择
      this.$nextTick(() => {
        this.roleForm.setFieldsValue({
          modelId: undefined
        });
      });
    },
    
    // 处理模型选择变更
    handleModelChange(value) {
      // 根据模型类型获取模型信息
      if (this.selectedModelType === MODEL_TYPE.LLM) {
        const model = this.modelItems.find(m => m.configId === value);
        if (model) {
          this.roleForm.setFieldsValue({
            roleDesc: this.editingRoleDesc
          });
        }
      } else if (this.selectedModelType === MODEL_TYPE.AGENT) {
        const agentList = this.selectedModelProvider === PROVIDER.COZE ? this.cozeAgents : this.difyAgents;
        const agent = agentList.find(a => a.configId === value);
        if (agent) {
          this.roleForm.setFieldsValue({
            roleDesc: agent.agentDesc || ''
          });
        }
      }
    },
    
    // 处理语音提供商变更
    handleProviderChange(value) {
      this.selectedProvider = value;
      
      // 根据提供商加载TTS配置
      if (value === 'edge') {
        // Edge使用默认配置
        this.ttsConfigs = [];
        this.selectedTtsId = 'edge_default';
        this.$nextTick(() => {
          this.roleForm.setFieldsValue({
            ttsId: 'edge_default'
          });
        });
      } else {
        // 加载TTS配置
        this.loadTtsConfigs(value);
      }
      
      // 重置性别选择
      this.selectedGender = '';
      this.$nextTick(() => {
        this.roleForm.setFieldsValue({
          gender: '',
          voiceName: this.defaultVoiceName
        });
      });
    },
    
    // 处理TTS配置变更
    handleTtsConfigChange(value) {
      this.selectedTtsId = value;
    },
    
    // 处理提示词模式变更
    handlePromptModeChange(e) {
      if (e.target.value === 'template' && this.selectedTemplateId) {
        const template = this.promptTemplates.find(t => t.templateId === this.selectedTemplateId);
        if (template) {
          this.roleForm.setFieldsValue({
            roleDesc: template.templateContent
          });
        }
      }
    },
    
    // 处理模板选择变更
    handleTemplateChange(templateId) {
      const template = this.promptTemplates.find(t => t.templateId === templateId);
      if (template) {
        this.roleForm.setFieldsValue({
          roleDesc: template.templateContent
        });
      }
    },
    
    // 跳转到模板管理页面
    goToTemplateManager() {
      this.$router.push('/template');
    },
    
    // 处理VAD折叠面板变化
    handleVadCollapseChange(activeKeys) {
      // 当折叠面板展开时（activeKeys包含'1'）
      if (activeKeys && activeKeys.includes('1') && this.pendingVadValues) {
        // 使用 $nextTick 确保DOM已更新
        this.$nextTick(() => {
          this.roleForm.setFieldsValue(this.pendingVadValues);
          // 清空待设置的值
          this.pendingVadValues = null;
        });
      }
    },
    
    // 处理模型折叠面板变化
    handleModelCollapseChange(activeKeys) {
      // 当折叠面板展开时（activeKeys包含'1'）
      if (activeKeys && activeKeys.includes('1') && this.pendingModelValues) {
        // 使用 $nextTick 确保DOM已更新
        this.$nextTick(() => {
          this.roleForm.setFieldsValue(this.pendingModelValues);
          // 清空待设置的值
          this.pendingModelValues = null;
        });
      }
    },
    
    // 获取语音显示名称
    getVoiceDisplayName(voiceName, provider) {
      if (!voiceName) return '-';
      
      let voices;
      if (provider === 'aliyun') {
        voices = this.aliyunVoices;
      } else if (provider === 'volcengine') {
        voices = this.volcengineVoices;
      } else if (provider === 'xfyun') {
        voices = this.xfyunVoices;
      } else if (provider === 'minimax') {
        voices = this.minimaxVoices;
      } else {
        voices = this.edgeVoices;
      }

      const voice = voices.find(v => v.value === voiceName);
      return voice ? voice.label : voiceName;
    },

    // 获取语音显示Tag颜色
    // TODO 这种方式感觉太冗余了，后期考虑怎么整合所有提供商到一个文件中统一处理
    getVoiceTagColor(provider) {
      switch (provider) {
        case "aliyun":
          return "orange";
        case "volcengine":
          return "blue";
        case "xfyun":
          return "cyan";
        case "minimax":
          return "red";
        default:
          return "green"
      }
    },

    // 格式化提供商名称
    formatProviderName(provider) {
      return provider ? provider.charAt(0).toUpperCase() + provider.slice(1) : 'Edge';
    },
    
    // 获取项目名称的辅助方法
    getItemName(items, idField, id, nameField) {
      if (!items || !items.length) return "";
      const item = items.find(item => item[idField] === id);
      return item ? item[nameField] : "";
    },

    getAvatarUrl(avatar) {
      return getResourceUrl(avatar);
    },

    // 头像上传前检查
    beforeAvatarUpload(file) {
      const isImage = file.type.startsWith('image/');
      const isLt2M = file.size / 1024 / 1024 < 2;

      if (!isImage) {
        this.$message.error('只能上传图片文件!');
        return false;
      }
      if (!isLt2M) {
        this.$message.error('图片大小不能超过2MB!');
        return false;
      }

      // 创建预览URL
      this.avatarFile = file;

      // 立即上传图片
      this.uploadAvatarFile(file)
        .then(url => {
          this.avatarUrl = url;
          this.avatarLoading = false;
        })
        .catch(error => {
          this.$message.error('头像上传失败: ' + error);
          this.avatarLoading = false;
        });
        
        return false; // 阻止自动上传，我们会在提交表单时手动上传
    },

    // 上传头像文件并获取URL
    uploadAvatarFile(file) {
      return new Promise((resolve, reject) => {
        // 创建FormData对象
        const formData = new FormData();
        formData.append('file', file);
        formData.append('type', 'avatar'); // 指定上传类型为头像

        // 使用XMLHttpRequest发送请求，确保正确设置content-type
        const xhr = new XMLHttpRequest();
        xhr.open('POST', api.upload, true);

        // 设置请求完成回调
        xhr.onload = function () {
          if (xhr.status === 200) {
            try {
              const response = JSON.parse(xhr.responseText);
              if (response.code === 200) {
                resolve(response.url);
              } else {
                reject(new Error(response.message || '上传失败'));
              }
            } catch (e) {
              reject(new Error('解析响应失败'));
            }
          } else {
            reject(new Error('上传失败，状态码: ' + xhr.status));
          }
        };

        // 设置错误回调
        xhr.onerror = function () {
          reject(new Error('网络错误'));
        };

        // 发送请求
        xhr.send(formData);
      });
    },

    // 移除头像
    removeAvatar() {
      this.avatarUrl = '';
      this.avatarFile = null;
    },
  }
}
</script>

<style scoped>
.ant-collapse {
  background: transparent;
}

.ant-collapse-header {
  font-weight: bold;
  color: #1890ff !important;
}

/* 自定义折叠面板样式 */
>>> .ant-collapse-borderless > .ant-collapse-item {
  border-bottom: 1px dashed #e8e8e8;
}

>>> .ant-collapse-borderless > .ant-collapse-item:last-child {
  border-bottom: none;
}

/* 表单项标签对齐 */
>>> .ant-form-item-label {
  text-align: left;
}

/* 分隔线样式 */
.ant-divider-with-text-left {
  margin: 16px 0;
  font-weight: bold;
  color: rgba(0, 0, 0, 0.85);
}

/* 头像上传样式 */
.avatar-uploader-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 上传组件样式 */
.avatar-uploader {
  cursor: pointer;
}

/* 上传内容区域 */
.avatar-content {
  position: relative;
  width: 128px;
  height: 128px;
  border-radius: 64px;
  background-color: #fafafa;
  border: 1px dashed #d9d9d9;
  overflow: hidden;
  transition: all 0.3s;
}

.avatar-content:hover {
  border-color: #1890ff;
}

/* 头像图片 */
.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 占位符 */
.avatar-placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #999;
}

.avatar-placeholder .anticon {
  font-size: 32px;
  margin-bottom: 8px;
}

.avatar-placeholder p {
  margin: 0;
}

/* 悬浮遮罩 - 整个区域都显示 */
.avatar-hover-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  opacity: 0;
  transition: opacity 0.3s;
}

.avatar-content:hover .avatar-hover-mask {
  opacity: 1;
}

.avatar-hover-mask .anticon {
  font-size: 24px;
  margin-bottom: 8px;
}

.avatar-hover-mask p {
  margin: 0;
}

/* 删除按钮 */
.avatar-remove-btn {
  margin-top: 8px;
}

/* 提示文字 */
.avatar-tip {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 12px;
}
</style>