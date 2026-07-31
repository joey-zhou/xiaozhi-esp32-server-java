-- 火山引擎内置音色 1.0 -> 2.0 迁移
--
-- 背景：TTS 升级到豆包语音合成大模型 2.0（seed-tts-2.0）后，1.0 时代的音色 ID
-- （_mars_bigtts / _moon_bigtts / ICL_*_tob）在 2.0 资源下不存在，直接调用会返回
-- 55000000 资源不匹配。sys_role.voiceName 存的正是音色 ID，需一次性刷新为 2.0 命名。
--
-- 共 108 个音色有 2.0 版本；方言、部分英澳英语与多语种音色暂无 2.0 版本，保持不变。
-- 注意：1.0 的「多情感」变体在 2.0 中已合并入普通音色（2.0 原生支持指令遵循），
-- 故存在两个旧 ID 映射到同一新 ID 的情况，属预期行为。

-- 1. 角色绑定的音色 ID
-- 东方浩然
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_dongfanghaoran_uranus_bigtts' WHERE `voiceName` = 'zh_male_dongfanghaoran_moon_bigtts';
-- 亮嗓萌仔
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_liangsangmengzai_uranus_bigtts' WHERE `voiceName` = 'zh_male_dongmanhaimian_mars_bigtts';
-- 优柔公子
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_yourougongzi_tob' WHERE `voiceName` = 'ICL_zh_male_yourougongzi_tob';
-- 优柔帮主
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_youroubangzhu_tob' WHERE `voiceName` = 'ICL_zh_male_youroubangzhu_tob';
-- 佩奇猪
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_peiqi_uranus_bigtts' WHERE `voiceName` = 'zh_female_peiqi_mars_bigtts';
-- 俏皮女声
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_qiaopinv_uranus_bigtts' WHERE `voiceName` = 'zh_female_qiaopinvsheng_mars_bigtts';
-- 假小子
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_jiaxiaozi_tob' WHERE `voiceName` = 'ICL_zh_female_jiaxiaozi_tob';
-- 傲娇女友
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_aojiaonvyou_tob' WHERE `voiceName` = 'ICL_zh_female_aojiaonvyou_tob';
-- 傲娇霸总
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_aojiaobazong_uranus_bigtts' WHERE `voiceName` = 'zh_male_aojiaobazong_moon_bigtts';
-- 傲慢娇声
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_aomanjiaosheng_tob' WHERE `voiceName` = 'ICL_zh_female_aomanjiaosheng_tob';
-- 傲慢少爷
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_aomanshaoye_tob' WHERE `voiceName` = 'ICL_zh_male_aomanshaoye_tob';
-- 傲气凌人
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_aoqilingren_tob' WHERE `voiceName` = 'ICL_zh_male_aiqilingren_tob';
-- 儒雅才俊
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_ruyacaijun_tob' WHERE `voiceName` = 'ICL_zh_male_ruyacaijun_tob';
-- 儒雅青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_ruyaqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_ruyaqingnian_mars_bigtts';
-- 冷峻上司
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_lengjunshangsi_tob' WHERE `voiceName` = 'ICL_zh_male_lengjunshangsi_tob';
-- 冷淡疏离
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_lengdanshuli_tob' WHERE `voiceName` = 'ICL_zh_male_lengdanshuli_tob';
-- 反卷青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_fanjuanqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_fanjuanqingnian_mars_bigtts';
-- 古风少御
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_gufengshaoyu_uranus_bigtts' WHERE `voiceName` = 'zh_female_gufengshaoyu_mars_bigtts';
-- 可爱女生
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_keainvsheng_tob' WHERE `voiceName` = 'ICL_zh_female_keainvsheng_tob';
-- 和蔼奶奶
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_heainainai_tob' WHERE `voiceName` = 'ICL_zh_female_heainainai_tob';
-- 四郎
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_silang_uranus_bigtts' WHERE `voiceName` = 'zh_male_silang_mars_bigtts';
-- 固执病娇
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_guzhibingjiao_tob' WHERE `voiceName` = 'ICL_zh_male_guzhibingjiao_tob';
-- 天才童声
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_tiancaitongsheng_uranus_bigtts' WHERE `voiceName` = 'zh_male_tiancaitongsheng_mars_bigtts';
-- 奶气萌娃
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_naiqimengwa_uranus_bigtts' WHERE `voiceName` = 'zh_male_naiqimengwa_mars_bigtts';
-- 妩媚御姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_wumeiyujie_tob' WHERE `voiceName` = 'ICL_zh_female_wumeiyujie_tob';
-- 娇弱萝莉
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_jiaoruoluoli_tob' WHERE `voiceName` = 'ICL_zh_female_jiaoruoluoli_tob';
-- 娇憨女王
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_jiaohannvwang_tob' WHERE `voiceName` = 'ICL_zh_female_jiaohannvwang_tob';
-- 婆婆
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_popo_uranus_bigtts' WHERE `voiceName` = 'zh_female_popo_mars_bigtts';
-- 学霸男同桌
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_xuebanantongzhuo_tob' WHERE `voiceName` = 'ICL_zh_male_xuebanantongzhuo_tob';
-- 少儿故事
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_shaoergushi_uranus_bigtts' WHERE `voiceName` = 'zh_female_shaoergushi_mars_bigtts';
-- 少年将军
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_shaonianjiangjun_tob' WHERE `voiceName` = 'ICL_zh_male_shaonianjiangjun_tob';
-- 少年梓辛/Brayan
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_shaonianzixin_uranus_bigtts' WHERE `voiceName` = 'zh_male_shaonianzixin_moon_bigtts';
-- 幽默叔叔
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_youmoshushu_tob' WHERE `voiceName` = 'ICL_zh_male_youmoshushu_tob';
-- 幽默大爷
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_youmodaye_tob' WHERE `voiceName` = 'ICL_zh_male_youmodaye_tob';
-- 广告解说
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_guanggaojieshuo_uranus_bigtts' WHERE `voiceName` = 'zh_male_chunhui_mars_bigtts';
-- 开朗姐姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_kailangjiejie_uranus_bigtts' WHERE `voiceName` = 'zh_female_kailangjiejie_moon_bigtts';
-- 开朗轻快
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_kailangqingkuai_tob' WHERE `voiceName` = 'ICL_zh_male_kailangqingkuai_tob';
-- 心灵鸡汤
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_xinlingjitang_uranus_bigtts' WHERE `voiceName` = 'zh_female_xinlingjitang_moon_bigtts';
-- 性感御姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_xingganyujie_tob' WHERE `voiceName` = 'ICL_zh_female_xingganyujie_tob';
-- 悬疑解说
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_xuanyijieshuo_uranus_bigtts' WHERE `voiceName` = 'zh_male_changtianyi_mars_bigtts';
-- 憨厚敦实
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_hanhoudunshi_tob' WHERE `voiceName` = 'ICL_zh_male_hanhoudunshi_tob';
-- 懒音绵宝
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_lanyinmianbao_uranus_bigtts' WHERE `voiceName` = 'zh_male_lanxiaoyang_mars_bigtts';
-- 成熟姐姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_chengshujiejie_tob' WHERE `voiceName` = 'ICL_zh_female_chengshujiejie_tob';
-- 撒娇学妹
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_sajiaoxuemei_uranus_bigtts' WHERE `voiceName` = 'zh_female_yuanqinvyou_moon_bigtts';
-- 撒娇粘人
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_sajiaonianren_tob' WHERE `voiceName` = 'ICL_zh_male_sajiaonianren_tob';
-- 擎苍
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_qingcang_uranus_bigtts' WHERE `voiceName` = 'zh_male_qingcang_mars_bigtts';
-- 暖心体贴
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_nuanxintitie_tob' WHERE `voiceName` = 'ICL_zh_male_nuanxintitie_tob';
-- 暖心学姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_nuanxinxuejie_tob' WHERE `voiceName` = 'ICL_zh_female_nuanxinxuejie_tob';
-- 柔美女友
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_roumeinvyou_uranus_bigtts' WHERE `voiceName` = 'zh_female_sajiaonvyou_moon_bigtts';
-- 樱桃丸子
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_yingtaowanzi_uranus_bigtts' WHERE `voiceName` = 'zh_female_yingtaowanzi_mars_bigtts';
-- 正直青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_zhengzhiqingnian_tob' WHERE `voiceName` = 'ICL_zh_male_zhengzhiqingnian_tob';
-- 武则天
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_wuzetian_uranus_bigtts' WHERE `voiceName` = 'zh_female_wuzetian_mars_bigtts';
-- 活力小哥
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_huolixiaoge_uranus_bigtts' WHERE `voiceName` = 'zh_male_yangguangqingnian_mars_bigtts';
-- 活泼刁蛮
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_huopodiaoman_tob' WHERE `voiceName` = 'ICL_zh_female_huopodiaoman_tob';
-- 活泼女孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_huoponvhai_tob' WHERE `voiceName` = 'ICL_zh_female_huoponvhai_tob';
-- 活泼爽朗
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_huoposhuanglang_tob' WHERE `voiceName` = 'ICL_zh_male_huoposhuanglang_tob';
-- 深夜播客
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_shenyeboke_uranus_bigtts' WHERE `voiceName` = 'zh_male_shenyeboke_moon_bigtts';
-- 清新女声
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_qingxinnvsheng_uranus_bigtts' WHERE `voiceName` = 'zh_female_qingxinnvsheng_mars_bigtts';
-- 清澈梓梓
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_qingchezizi_uranus_bigtts' WHERE `voiceName` = 'zh_female_qingchezizi_moon_bigtts';
-- 清爽男大
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_qingshuangnanda_uranus_bigtts' WHERE `voiceName` = 'zh_male_qingshuangnanda_mars_bigtts';
-- 渊博小叔
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_yuanboxiaoshu_uranus_bigtts' WHERE `voiceName` = 'zh_male_yuanboxiaoshu_moon_bigtts';
-- 温暖阿虎/Alvin
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_wennuanahu_uranus_bigtts' WHERE `voiceName` = 'zh_male_wennuanahu_moon_bigtts';
-- 温柔小哥
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_wenrouxiaoge_uranus_bigtts' WHERE `voiceName` = 'zh_male_wenrouxiaoge_mars_bigtts';
-- 温柔小雅
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_wenrouxiaoya_uranus_bigtts' WHERE `voiceName` = 'zh_female_wenrouxiaoya_moon_bigtts';
-- 温柔文雅
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_wenrouwenya_tob' WHERE `voiceName` = 'ICL_zh_female_wenrouwenya_tob';
-- 温柔淑女
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_wenroushunv_uranus_bigtts' WHERE `voiceName` = 'zh_female_wenroushunv_mars_bigtts';
-- 温柔男同桌
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_wenrounantongzhuo_tob' WHERE `voiceName` = 'ICL_zh_male_wenrounantongzhuo_tob';
-- 湾湾小何
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_xiaohe_uranus_bigtts' WHERE `voiceName` = 'zh_female_wanwanxiaohe_moon_bigtts';
-- 潇洒随性
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_xiaosasuixing_tob' WHERE `voiceName` = 'ICL_zh_male_xiaosasuixing_tob';
-- 灿灿/Shiny
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_cancan_uranus_bigtts' WHERE `voiceName` = 'zh_female_cancan_mars_bigtts';
-- 熊二
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_xionger_uranus_bigtts' WHERE `voiceName` = 'zh_male_xionger_mars_bigtts';
-- 爽快思思/Skye
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_shuangkuaisisi_uranus_bigtts' WHERE `voiceName` = 'zh_female_shuangkuaisisi_emo_v2_mars_bigtts';
-- 爽快思思/Skye
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_shuangkuaisisi_uranus_bigtts' WHERE `voiceName` = 'zh_female_shuangkuaisisi_moon_bigtts';
-- 猴哥
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_sunwukong_uranus_bigtts' WHERE `voiceName` = 'zh_male_sunwukong_mars_bigtts';
-- 率真小伙
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_shuaizhenxiaohuo_tob' WHERE `voiceName` = 'ICL_zh_male_shuaizhenxiaohuo_tob';
-- 甜美小源
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_tianmeixiaoyuan_uranus_bigtts' WHERE `voiceName` = 'zh_female_tianmeixiaoyuan_moon_bigtts';
-- 甜美悦悦
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_tianmeiyueyue_uranus_bigtts' WHERE `voiceName` = 'zh_female_tianmeiyueyue_moon_bigtts';
-- 病娇哥哥
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_bingjiaogege_tob' WHERE `voiceName` = 'ICL_zh_male_bingjiaogege_tob';
-- 病娇姐姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_bingjiaojiejie_tob' WHERE `voiceName` = 'ICL_zh_female_bingjiaojiejie_tob';
-- 病娇弟弟
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_bingjiaodidi_tob' WHERE `voiceName` = 'ICL_zh_male_bingjiaodidi_tob';
-- 病娇白莲
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_bingjiaobailian_tob' WHERE `voiceName` = 'ICL_zh_male_bingjiaobailian_tob';
-- 病娇萌妹
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_bingjiaomengmei_tob' WHERE `voiceName` = 'ICL_zh_female_bingjiaomengmei_tob';
-- 病弱少女
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_bingruoshaonv_tob' WHERE `voiceName` = 'ICL_zh_female_bingruoshaonv_tob';
-- 知性女声
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_zhixingnv_uranus_bigtts' WHERE `voiceName` = 'zh_female_zhixingnvsheng_mars_bigtts';
-- 知性温婉
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_zhixingwenwan_tob' WHERE `voiceName` = 'ICL_zh_female_zhixingwenwan_tob';
-- 磁性解说男声/Morgan
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_cixingjieshuonan_uranus_bigtts' WHERE `voiceName` = 'zh_male_jieshuonansheng_mars_bigtts';
-- 神秘法师
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_shenmifashi_tob' WHERE `voiceName` = 'ICL_zh_male_shenmifashi_tob';
-- 纯真学弟
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_chunzhenxuedi_tob' WHERE `voiceName` = 'ICL_zh_male_chunzhenxuedi_tob';
-- 绿茶小哥
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_lvchaxiaoge_tob' WHERE `voiceName` = 'ICL_zh_male_lvchaxiaoge_tob';
-- 腹黑公子
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_fuheigongzi_tob' WHERE `voiceName` = 'ICL_zh_male_fuheigongzi_tob';
-- 萌丫头/Cutey
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_mengyatou_uranus_bigtts' WHERE `voiceName` = 'zh_female_mengyatou_mars_bigtts';
-- 解说小明
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_jieshuoxiaoming_uranus_bigtts' WHERE `voiceName` = 'zh_male_jieshuoxiaoming_moon_bigtts';
-- 诡异神秘
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_guiyishenmi_tob' WHERE `voiceName` = 'ICL_zh_male_guiyishenmi_tob';
-- 调皮公主
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_tiaopigongzhu_tob' WHERE `voiceName` = 'ICL_zh_female_tiaopigongzhu_tob';
-- 贴心女友
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_tiexinnvyou_tob' WHERE `voiceName` = 'ICL_zh_female_tiexinnvyou_tob';
-- 贴心女声/Candy
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_tiexinnvsheng_uranus_bigtts' WHERE `voiceName` = 'zh_female_tiexinnvsheng_mars_bigtts';
-- 贴心男友
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_tiexinnanyou_tob' WHERE `voiceName` = 'ICL_zh_male_tiexinnanyou_tob';
-- 邻家女孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_linjianvhai_uranus_bigtts' WHERE `voiceName` = 'zh_female_linjianvhai_moon_bigtts';
-- 邻家男孩
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_linjiananhai_uranus_bigtts' WHERE `voiceName` = 'zh_male_linjiananhai_moon_bigtts';
-- 邻居阿姨
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_female_linjuayi_tob' WHERE `voiceName` = 'ICL_zh_female_linjuayi_tob';
-- 阳光青年
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_yangguangqingnian_uranus_bigtts' WHERE `voiceName` = 'zh_male_yangguangqingnian_moon_bigtts';
-- 霸气青叔
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_male_baqiqingshu_uranus_bigtts' WHERE `voiceName` = 'zh_male_baqiqingshu_mars_bigtts';
-- 青涩小生
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'ICL_uranus_zh_male_qingsexiaosheng_tob' WHERE `voiceName` = 'ICL_zh_male_qingsenaigou_tob';
-- 顾姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_gujie_uranus_bigtts' WHERE `voiceName` = 'zh_female_gujie_mars_bigtts';
-- 高冷御姐
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_gaolengyujie_uranus_bigtts' WHERE `voiceName` = 'zh_female_gaolengyujie_moon_bigtts';
-- 魅力女友
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_meilinvyou_uranus_bigtts' WHERE `voiceName` = 'zh_female_meilinvyou_emo_v2_mars_bigtts';
-- 魅力女友
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_meilinvyou_uranus_bigtts' WHERE `voiceName` = 'zh_female_meilinvyou_moon_bigtts';
-- 鸡汤妹妹/Hope
UPDATE `xiaozhi`.`sys_role` SET `voiceName` = 'zh_female_jitangmei_uranus_bigtts' WHERE `voiceName` = 'zh_female_jitangmeimei_mars_bigtts';

-- 2. 清理旧音色的 TTS 音频缓存
-- 2.0 与 1.0 音质不同，缓存必须失效而非改名（sys_audio 上有 voiceName 唯一键，
-- 改名会撞键；保留旧缓存则会播放 1.0 音色的音频）。
DELETE FROM `xiaozhi`.`sys_audio` WHERE `voiceName` IN (
  'ICL_zh_female_aojiaonvyou_tob',
  'ICL_zh_female_aomanjiaosheng_tob',
  'ICL_zh_female_bingjiaojiejie_tob',
  'ICL_zh_female_bingjiaomengmei_tob',
  'ICL_zh_female_bingruoshaonv_tob',
  'ICL_zh_female_chengshujiejie_tob',
  'ICL_zh_female_heainainai_tob',
  'ICL_zh_female_huopodiaoman_tob',
  'ICL_zh_female_huoponvhai_tob',
  'ICL_zh_female_jiaohannvwang_tob',
  'ICL_zh_female_jiaoruoluoli_tob',
  'ICL_zh_female_jiaxiaozi_tob',
  'ICL_zh_female_keainvsheng_tob',
  'ICL_zh_female_linjuayi_tob',
  'ICL_zh_female_nuanxinxuejie_tob',
  'ICL_zh_female_tiaopigongzhu_tob',
  'ICL_zh_female_tiexinnvyou_tob',
  'ICL_zh_female_wenrouwenya_tob',
  'ICL_zh_female_wumeiyujie_tob',
  'ICL_zh_female_xingganyujie_tob',
  'ICL_zh_female_zhixingwenwan_tob',
  'ICL_zh_male_aiqilingren_tob',
  'ICL_zh_male_aomanshaoye_tob',
  'ICL_zh_male_bingjiaobailian_tob',
  'ICL_zh_male_bingjiaodidi_tob',
  'ICL_zh_male_bingjiaogege_tob',
  'ICL_zh_male_chunzhenxuedi_tob',
  'ICL_zh_male_fuheigongzi_tob',
  'ICL_zh_male_guiyishenmi_tob',
  'ICL_zh_male_guzhibingjiao_tob',
  'ICL_zh_male_hanhoudunshi_tob',
  'ICL_zh_male_huoposhuanglang_tob',
  'ICL_zh_male_kailangqingkuai_tob',
  'ICL_zh_male_lengdanshuli_tob',
  'ICL_zh_male_lengjunshangsi_tob',
  'ICL_zh_male_lvchaxiaoge_tob',
  'ICL_zh_male_nuanxintitie_tob',
  'ICL_zh_male_qingsenaigou_tob',
  'ICL_zh_male_ruyacaijun_tob',
  'ICL_zh_male_sajiaonianren_tob',
  'ICL_zh_male_shaonianjiangjun_tob',
  'ICL_zh_male_shenmifashi_tob',
  'ICL_zh_male_shuaizhenxiaohuo_tob',
  'ICL_zh_male_tiexinnanyou_tob',
  'ICL_zh_male_wenrounantongzhuo_tob',
  'ICL_zh_male_xiaosasuixing_tob',
  'ICL_zh_male_xuebanantongzhuo_tob',
  'ICL_zh_male_youmodaye_tob',
  'ICL_zh_male_youmoshushu_tob',
  'ICL_zh_male_youroubangzhu_tob',
  'ICL_zh_male_yourougongzi_tob',
  'ICL_zh_male_zhengzhiqingnian_tob',
  'zh_female_cancan_mars_bigtts',
  'zh_female_gaolengyujie_moon_bigtts',
  'zh_female_gufengshaoyu_mars_bigtts',
  'zh_female_gujie_mars_bigtts',
  'zh_female_jitangmeimei_mars_bigtts',
  'zh_female_kailangjiejie_moon_bigtts',
  'zh_female_linjianvhai_moon_bigtts',
  'zh_female_meilinvyou_emo_v2_mars_bigtts',
  'zh_female_meilinvyou_moon_bigtts',
  'zh_female_mengyatou_mars_bigtts',
  'zh_female_peiqi_mars_bigtts',
  'zh_female_popo_mars_bigtts',
  'zh_female_qiaopinvsheng_mars_bigtts',
  'zh_female_qingchezizi_moon_bigtts',
  'zh_female_qingxinnvsheng_mars_bigtts',
  'zh_female_sajiaonvyou_moon_bigtts',
  'zh_female_shaoergushi_mars_bigtts',
  'zh_female_shuangkuaisisi_emo_v2_mars_bigtts',
  'zh_female_shuangkuaisisi_moon_bigtts',
  'zh_female_tianmeixiaoyuan_moon_bigtts',
  'zh_female_tianmeiyueyue_moon_bigtts',
  'zh_female_tiexinnvsheng_mars_bigtts',
  'zh_female_wanwanxiaohe_moon_bigtts',
  'zh_female_wenroushunv_mars_bigtts',
  'zh_female_wenrouxiaoya_moon_bigtts',
  'zh_female_wuzetian_mars_bigtts',
  'zh_female_xinlingjitang_moon_bigtts',
  'zh_female_yingtaowanzi_mars_bigtts',
  'zh_female_yuanqinvyou_moon_bigtts',
  'zh_female_zhixingnvsheng_mars_bigtts',
  'zh_male_aojiaobazong_moon_bigtts',
  'zh_male_baqiqingshu_mars_bigtts',
  'zh_male_changtianyi_mars_bigtts',
  'zh_male_chunhui_mars_bigtts',
  'zh_male_dongfanghaoran_moon_bigtts',
  'zh_male_dongmanhaimian_mars_bigtts',
  'zh_male_fanjuanqingnian_mars_bigtts',
  'zh_male_jieshuonansheng_mars_bigtts',
  'zh_male_jieshuoxiaoming_moon_bigtts',
  'zh_male_lanxiaoyang_mars_bigtts',
  'zh_male_linjiananhai_moon_bigtts',
  'zh_male_naiqimengwa_mars_bigtts',
  'zh_male_qingcang_mars_bigtts',
  'zh_male_qingshuangnanda_mars_bigtts',
  'zh_male_ruyaqingnian_mars_bigtts',
  'zh_male_shaonianzixin_moon_bigtts',
  'zh_male_shenyeboke_moon_bigtts',
  'zh_male_silang_mars_bigtts',
  'zh_male_sunwukong_mars_bigtts',
  'zh_male_tiancaitongsheng_mars_bigtts',
  'zh_male_wennuanahu_moon_bigtts',
  'zh_male_wenrouxiaoge_mars_bigtts',
  'zh_male_xionger_mars_bigtts',
  'zh_male_yangguangqingnian_mars_bigtts',
  'zh_male_yangguangqingnian_moon_bigtts',
  'zh_male_yuanboxiaoshu_moon_bigtts'
);
