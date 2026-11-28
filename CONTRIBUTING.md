# 贡献指南 / Contributing Guide

欢迎参与 `xiaozhi-esp32-server-java` 的开发与改进！在提交 Pull Request 前，请先阅读以下说明。

## 一、大模型供应商（LLM Provider）收录政策

**本项目不再接受为第三方 OpenAI 兼容中转站、API 聚合网关或非主流模型托管平台新增内置供应商预设的 Pull Request。**

- **内置清单范围**：仅收录主流大模型原厂、主流云厂商、主流开源本地推理框架，或因协议/鉴权机制存在差异而需要独立后端实现的供应商。
- **如何接入其他 OpenAI 兼容服务**：系统已内置通用的 **`OpenAI-API-Compatible`** 供应商入口，用户只需在管理后台选择该选项并填写对应的 `API URL`（以 `/v1` 结尾）、`API Key` 及模型名称即可使用，无需修改代码。
- **不满足上述条件的「新增供应商 / 预设」PR 将直接关闭**。

### LLM Provider Integration Policy

**We do not accept Pull Requests that add built-in presets for third-party OpenAI-compatible gateways, resellers, API aggregators, or non-major hosted inference services.**

- **Built-in catalog scope**: Reserved for major foundation model providers, major cloud providers, mainstream self-hosted inference frameworks, or providers requiring dedicated protocol/authentication handling on the backend.
- **Connecting other OpenAI-compatible services**: Use the built-in **`OpenAI-API-Compatible`** provider directly with your custom `API URL` (ending with `/v1`), `API Key`, and model name—no code changes are required.
- Unsolicited PRs adding vendor-specific OpenAI-compatible presets will be closed.

## 二、提交 Pull Request 建议

1. 较大的功能改动建议先提 Issue 讨论方案，确认后再编码。
2. 保持 PR 聚焦单一主题，避免夹带无关格式化或大面积重排。
3. 提交前请确保修改范围内的测试与检查通过：
   - 前端：`cd web && npm run type-check && npm run test:run`
   - 后端：`mvn test`（或对应模块的单测）
