<template>
  <a-modal v-model="visible" title="设备详情" @ok="handleOk" @cancel="handleClose">
    <a-form :form="form" :label-col="{ span: 5 }" :wrapper-col="{ span: 12 }">
      <a-form-item label="设备名称">
        <a-input v-model="form.deviceName"/>
      </a-form-item>
      <a-form-item label="绑定角色">
        <a-select v-model="form.roleId">
          <a-select-option v-for="i in roleItems" :key="i.roleId" :value="i.roleId" class="model-option">{{ i.roleName }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="对话模型">
        <a-cascader 
          style="width: 100%"
          :options="modelOptions" 
          :value="getCascaderValue()"
          @change="handleModelChange" 
          placeholder="请选择模型"
          expandTrigger="hover"
          :allowClear=false />
      </a-form-item>
      <a-form-item label="语音识别">
        <a-select v-model="form.sttId">
          <a-select-option v-for="i in sttItems" :key="i.sttId" :value="i.sttId" class="model-option">{{ i.sttName }}</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
    
    <template slot="footer">
      <a-popconfirm
        title="确定要清除该设备的所有对话记忆吗？此操作不可恢复。"
        ok-text="确定"
        cancel-text="取消"
        @confirm="handleClearMemory"
      >
        <a-button key="clear" type="danger" :loading="clearMemoryLoading">
          清除记忆
        </a-button>
      </a-popconfirm>
      <a-button key="back" @click="handleClose">
        取消
      </a-button>
      <a-button key="submit" type="primary" @click="handleOk">
        确定
      </a-button>
    </template>
  </a-modal>
</template>

<script>
export default {
  name: "DeviceEditDialog",
  props:{
    visible: Boolean,
    modelItems: Array,
    sttItems: Array,
    roleItems: Array,
    current: Object,
    agentItems: Array,
    clearMemoryLoading: Boolean
  },
  data(){
    return {
      form: this.$form.createForm(this, {
        deviceId: "",
        deviceName: "",
        modelId: null,
        modelType: "",
        sttId: null,
        roleId: null,
        provider: ""
      }),
      modelOptions: [
        {
          value: "llm",
          label: "LLM模型",
          children: []
        },
        {
          value: "agent",
          label: "智能体",
          children: [
            {
              value: "coze",
              label: "Coze",
              children: []
            }
          ]
        }
      ],
      providerMap: {}, // 用于存储按提供商分组的模型
    }
  },
  methods:{
    handleClose(){
      this.$emit("close");
    },
    handleOk(){
      this.$emit("submit", this.form)
    },
    
    // 新增清除记忆方法
    handleClearMemory() {
      this.$emit("clear-memory", this.form);
    },
    
    // 处理级联选择器变更 - 修改为三级结构
    handleModelChange(value) {
      if (!value || value.length < 3) return;
      
      const modelType = value[0]; // llm 或 agent
      const provider = value[1];  // 提供商
      const modelId = Number(value[2]); // 模型ID
      
      this.form.modelId = modelId; // 保存modelId，这是传给后端的值
      this.form.modelType = modelType; // 保存模型类型
      this.form.provider = provider; // 保存提供商
      
      // 根据类型设置显示名称和描述
      if (modelType === "llm") {
        const model = this.modelItems.find(item => Number(item.configId) === modelId);
        if (model) {
          this.form.modelName = model.configName || model.modelName;
          this.form.modelDesc = model.configDesc || model.modelDesc;
        }
      } else if (modelType === "agent") {
        const agent = this.agentItems.find(item => Number(item.configId) === modelId);
        if (agent) {
          this.form.modelName = agent.agentName;
          this.form.modelDesc = agent.agentDesc || '';
        }
      }
    },
    
    // 获取级联选择器的值 - 修改为三级结构
    getCascaderValue() {
      if (!this.form.modelId) return [];
      
      // 智能体只有一个提供商 - coze
      if (this.form.modelType === 'agent') {
        return ["agent", "coze", this.form.modelId];
      } else if (this.form.modelType === 'llm' && this.form.provider) {
        // LLM模型需要提供商信息
        return ["llm", this.form.provider, this.form.modelId];
      } else {
        // 默认情况下，尝试在两个列表中查找
        const modelId = Number(this.form.modelId);
        const isAgent = this.agentItems.some(a => Number(a.configId) === modelId);
        if (isAgent) {
          return ["agent", "coze", modelId];
        } else {
          // 尝试找到LLM模型的提供商
          const model = this.modelItems.find(m => Number(m.configId) === modelId);
          if (model && model.provider) {
            return ["llm", model.provider, modelId];
          }
          // 如果找不到提供商，返回空数组
          return [];
        }
      }
    },
    
    // 更新模型选项 - 修改为三级结构
    updateModelOptions() {
      // 初始化提供商映射
      this.providerMap = {};
      
      // 按提供商分组LLM模型
      if (this.modelItems && this.modelItems.length > 0) {
        this.modelItems.forEach(item => {
          const provider = item.provider || "other";
          if (!this.providerMap[provider]) {
            this.providerMap[provider] = [];
          }
          this.providerMap[provider].push(item);
        });
      }
      // 添加LLM模型提供商
      for (const provider in this.providerMap) {
        const models = this.providerMap[provider];
        const providerOption = {
          value: provider,
          label: provider.charAt(0).toUpperCase() + provider.slice(1),
          children: []
        };
        
        // 添加该提供商下的所有模型
        models.forEach(model => {
          providerOption.children.push({
            value: model.configId,
            label: model.configName,
            isLeaf: true,
            data: model
          });
        });
        
        // 将提供商选项添加到LLM类别下
        this.modelOptions[0].children.push(providerOption);
      }
      
      // 添加智能体选项
      if (this.agentItems && this.agentItems.length > 0) {
        this.agentItems.forEach(item => {
          this.modelOptions[1].children[0].children.push({
            value: item.configId,
            label: item.agentName,
            isLeaf: true,
            data: item
          });
        });
      }
    }
  },
  watch:{
    visible(val){
      if(val){
        this.form = Object.assign({}, this.$props.current);
        this.updateModelOptions();
      }
    },
    modelItems() {
      this.updateModelOptions();
    },
    agentItems() {
      this.updateModelOptions();
    }
  }
}
</script>

<style lang="scss" scoped>
>>> .ant-input{
  text-align: center !important;
}

/* 确保级联选择器内容居中 */
>>> .ant-cascader-picker-label {
  text-align: center !important;
  width: 100% !important;
}

</style>