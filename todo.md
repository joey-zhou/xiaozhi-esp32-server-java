我要你做,从新检查下：
1：基于db/init.sql 对照enitty 目录下实体类是不是不对。
2：entity实体类@Column 能省略就省略，实体类也要参考main分支里面的entity
3：你respositoy 功能方法参考分支main里面的dao。
4: 你帮我检查下aspect目录下为什么有的开启后项目起不来

1：法命名风格：main 分支使用 selectUserByUserId 这种 MyBatis 风格，但你要继续使用 JPA。我的建议是保持 JPA 的 findByUsername 风格，但确保方法签名（参数和返回值）与 main 分支一致。是否同意？假如main 分支没有的就用jpa 风格
2:generateCode 方法：main 分支的 UserMapper.generateCode(SysUser user) 接收实体对象，但当前 Repository 使用 generateCode(String code, String email, String tel)。是否改为接收实体对象？是的
3:resetDefault 方法：main 分支使用 resetDefault(SysRole role)，当前使用 resetDefault(Integer userId)。是否改为接收实体对象？是的