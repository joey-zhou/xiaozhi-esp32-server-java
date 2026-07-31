-- 火山引擎：移除无 2.0 版本的 1.0 音色
--
-- 1.0 资源（volc.service_type.10029）只接受旧版控制台的 App ID + Access Token 鉴权，
-- 与新版控制台 API Key 不兼容，调用会返回 45000010 Invalid X-Api-Key。
-- 因此音色列表已改为仅保留 2.0 音色，此处把仍绑定 1.0 音色的角色改指到同语种的 2.0 替代音色。
--
-- 说明：方言音色（京腔、粤语、四川话等）2.0 暂无对应版本，退到中文通用音色；
-- 如需方言效果，可改用 2.0 的 explicit_dialect 参数（支持东北话、陕西话、四川话）。
-- 1.0 的多情感音色在 2.0 中已合并入普通音色（2.0 原生支持情感与指令遵循）。

-- en_male_jackson_mars_bigtts -> Alex
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'en_male_alex_uranus_bigtts' WHERE `voiceName` = 'en_male_jackson_mars_bigtts';
-- en_female_anna_mars_bigtts -> Dacey
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'en_female_dacey_uranus_bigtts' WHERE `voiceName` = 'en_female_anna_mars_bigtts';
-- en_female_sarah_mars_bigtts -> Dacey
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'en_female_dacey_uranus_bigtts' WHERE `voiceName` = 'en_female_sarah_mars_bigtts';
-- multi_female_shuangkuaisisi_moon_bigtts -> Hana
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ja_female_bv522_uranus_bigtts' WHERE `voiceName` = 'multi_female_shuangkuaisisi_moon_bigtts';
-- multi_male_jingqiangkanye_moon_bigtts -> Ken
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ja_male_bv524_uranus_bigtts' WHERE `voiceName` = 'multi_male_jingqiangkanye_moon_bigtts';
-- multi_male_wanqudashu_moon_bigtts -> Ken
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ja_male_bv524_uranus_bigtts' WHERE `voiceName` = 'multi_male_wanqudashu_moon_bigtts';
-- multi_female_gaolengyujie_moon_bigtts -> Poppy
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ja_female_bv520_uranus_bigtts' WHERE `voiceName` = 'multi_female_gaolengyujie_moon_bigtts';
-- en_female_amanda_mars_bigtts -> Stokie
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'en_female_stokie_uranus_bigtts' WHERE `voiceName` = 'en_female_amanda_mars_bigtts';
-- en_male_smith_mars_bigtts -> Tim
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'en_male_tim_uranus_bigtts' WHERE `voiceName` = 'en_male_smith_mars_bigtts';
-- en_male_adam_mars_bigtts -> Tim
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'en_male_tim_uranus_bigtts' WHERE `voiceName` = 'en_male_adam_mars_bigtts';
-- en_male_dryw_mars_bigtts -> Tim
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'en_male_tim_uranus_bigtts' WHERE `voiceName` = 'en_male_dryw_mars_bigtts';
-- zh_female_roumeinvyou_emo_v2_mars_bigtts -> 柔美女友
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_roumeinvyou_uranus_bigtts' WHERE `voiceName` = 'zh_female_roumeinvyou_emo_v2_mars_bigtts';
-- zh_female_daimengchuanmei_moon_bigtts -> 邻家女孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_linjianvhai_uranus_bigtts' WHERE `voiceName` = 'zh_female_daimengchuanmei_moon_bigtts';
-- zh_female_meituojieer_moon_bigtts -> 邻家女孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_linjianvhai_uranus_bigtts' WHERE `voiceName` = 'zh_female_meituojieer_moon_bigtts';
-- zh_male_haoyuxiaoge_moon_bigtts -> 邻家男孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_linjiananhai_uranus_bigtts' WHERE `voiceName` = 'zh_male_haoyuxiaoge_moon_bigtts';
-- zh_male_guangxiyuanzhou_moon_bigtts -> 邻家男孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_linjiananhai_uranus_bigtts' WHERE `voiceName` = 'zh_male_guangxiyuanzhou_moon_bigtts';
-- zh_male_yuzhouzixuan_moon_bigtts -> 邻家男孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_linjiananhai_uranus_bigtts' WHERE `voiceName` = 'zh_male_yuzhouzixuan_moon_bigtts';
-- zh_male_beijingxiaoye_emo_v2_mars_bigtts -> 阳光青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_yangguangqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_beijingxiaoye_emo_v2_mars_bigtts';
-- zh_male_yangguangqingnian_emo_v2_mars_bigtts -> 阳光青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_yangguangqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_yangguangqingnian_emo_v2_mars_bigtts';
-- zh_male_jingqiangkanye_moon_bigtts -> 阳光青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_yangguangqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_jingqiangkanye_moon_bigtts';
-- zh_male_guozhoudege_moon_bigtts -> 阳光青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_yangguangqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_guozhoudege_moon_bigtts';
-- zh_male_beijingxiaoye_moon_bigtts -> 阳光青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_yangguangqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_beijingxiaoye_moon_bigtts';
-- zh_female_wanqudashu_moon_bigtts -> 高冷御姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_gaolengyujie_uranus_bigtts' WHERE `voiceName` = 'zh_female_wanqudashu_moon_bigtts';

-- 清理这些 1.0 音色的 TTS 音频缓存（音色已换，旧音频不可再用）
DELETE FROM `xiaozhi`.`sys_audio` WHERE `voiceName` IN (
  'en_female_amanda_mars_bigtts',
  'en_female_anna_mars_bigtts',
  'en_female_sarah_mars_bigtts',
  'en_male_adam_mars_bigtts',
  'en_male_dryw_mars_bigtts',
  'en_male_jackson_mars_bigtts',
  'en_male_smith_mars_bigtts',
  'multi_female_gaolengyujie_moon_bigtts',
  'multi_female_shuangkuaisisi_moon_bigtts',
  'multi_male_jingqiangkanye_moon_bigtts',
  'multi_male_wanqudashu_moon_bigtts',
  'zh_female_daimengchuanmei_moon_bigtts',
  'zh_female_meituojieer_moon_bigtts',
  'zh_female_roumeinvyou_emo_v2_mars_bigtts',
  'zh_female_wanqudashu_moon_bigtts',
  'zh_male_beijingxiaoye_emo_v2_mars_bigtts',
  'zh_male_beijingxiaoye_moon_bigtts',
  'zh_male_guangxiyuanzhou_moon_bigtts',
  'zh_male_guozhoudege_moon_bigtts',
  'zh_male_haoyuxiaoge_moon_bigtts',
  'zh_male_jingqiangkanye_moon_bigtts',
  'zh_male_yangguangqingnian_emo_v2_mars_bigtts',
  'zh_male_yuzhouzixuan_moon_bigtts'
);
