package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用户更新请求")
public class UserUpdateReq {

    @Schema(description = "新邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "新手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String tel;

    @Schema(description = "新密码")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    private String password;

    @Schema(description = "新姓名/昵称")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String name;

    @Schema(description = "新头像")
    private String avatar;
}
