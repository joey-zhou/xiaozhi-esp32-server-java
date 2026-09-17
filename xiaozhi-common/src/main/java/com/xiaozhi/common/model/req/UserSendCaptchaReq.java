package com.xiaozhi.common.model.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@Schema(description = "发送验证码请求")
public class UserSendCaptchaReq {

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String tel;

    @Schema(description = "用途类型", allowableValues = {"register", "forget"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类型不能为空")
    private String type;

    @AssertTrue(message = "邮箱和手机号至少填一项")
    @JsonIgnore
    @Schema(hidden = true)
    private boolean isEmailOrTelPresent() {
        return StringUtils.hasText(email) || StringUtils.hasText(tel);
    }
}
