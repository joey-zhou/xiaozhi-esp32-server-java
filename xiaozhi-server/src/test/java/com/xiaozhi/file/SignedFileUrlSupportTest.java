package com.xiaozhi.file;

import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.resp.LoginResp;
import com.xiaozhi.common.model.resp.PermissionTreeResp;
import com.xiaozhi.common.model.resp.UserResp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住响应体文件 URL 的扫描范围：顶层字段、集合元素、分页列表之外，
 * 嵌套对象里的字段同样要被处理——登录响应的头像就藏在 LoginResp.user 里。
 */
class SignedFileUrlSupportTest {

    @Test
    void appliesToFieldsInsideNestedObject() {
        UserResp user = new UserResp();
        user.setAvatar("uploads/avatar/a.png");
        LoginResp login = LoginResp.builder().token("t").user(user).build();

        SignedFileUrlSupport.apply(login, value -> value + "?signed");

        assertThat(login.getUser().getAvatar()).isEqualTo("uploads/avatar/a.png?signed");
    }

    @Test
    void skipsNullAndBlankValues() {
        UserResp blank = new UserResp();
        blank.setAvatar("");
        LoginResp login = LoginResp.builder().user(blank).build();

        SignedFileUrlSupport.apply(login, value -> value + "?signed");

        assertThat(login.getUser().getAvatar()).isEmpty();
        assertThat(LoginResp.builder().build().getUser()).isNull();
    }

    @Test
    void stillAppliesToPageAndCollectionElements() {
        UserResp first = new UserResp();
        first.setAvatar("uploads/avatar/a.png");
        UserResp second = new UserResp();
        second.setAvatar("uploads/avatar/b.png");
        PageResult<UserResp> page = new PageResult<UserResp>(List.of(first, second), 2L, 1, 10);

        SignedFileUrlSupport.apply(page, value -> value + "?signed");

        assertThat(page.getList()).extracting(UserResp::getAvatar)
                .containsExactly("uploads/avatar/a.png?signed", "uploads/avatar/b.png?signed");
    }

    @Test
    void doesNotRecurseForeverOnSelfReferencingTree() {
        PermissionTreeResp node = new PermissionTreeResp();
        node.setChildren(List.of(node));
        UserResp user = new UserResp();
        user.setAvatar("uploads/avatar/a.png");
        LoginResp login = LoginResp.builder().user(user).permissions(List.of(node)).build();

        SignedFileUrlSupport.apply(login, value -> value + "?signed");

        assertThat(login.getUser().getAvatar()).isEqualTo("uploads/avatar/a.png?signed");
    }
}
