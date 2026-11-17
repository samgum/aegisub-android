package io.github.samgum.aegisub.domain.sgmy

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.DialogueBlock
import kotlin.math.roundToInt

/**
 * 中日韩文本工具集（完整复刻 samgum/Aegisub SGMY 四大工具）：
 * 1. [SubtitleTextCleaner]：字幕文本清理（全角逗号转空格、中文句号清理/转空格、智能引号替换、连续空格检查与合并），严格跳过 `{...}` 标签块
 * 2. [ChineseConverter]：简繁中文互转（支持外部 ICU 转换器注入 + 内置高频词字映射回退，支持按样式与选中范围过滤）
 * 3. [PairedPunctuationChecker]：配对标点检查（中英文括号/书名号/直角引号/单双引号栈式校验，定位到行与字符位置）
 * 4. [FuriganaAnnotator]：日语假名注音（汉字提取、`@行#次:词=读音` 逐处与全局读音、平/片假名切换、上/下方注音、按样式应用、幂等更新）
 *
 * @author 伤感咩吖
 */
object SubtitleTextCleaner {

    data class Config(
        val replaceChineseCommas: Boolean = true,
        val cleanChinesePeriods: Boolean = true,
        val replaceSmartQuotes: Boolean = false,
        val checkDoubleSpaces: Boolean = true,
        val fixDoubleSpaces: Boolean = true,
        val selectedIds: Set<Long> = emptySet(),
    )

    data class Stats(
        val checkedLines: Int = 0,
        val changedLines: Int = 0,
        val commaReplacements: Int = 0,
        val commaLines: Int = 0,
        val periodsRemoved: Int = 0,
        val periodRemovedLines: Int = 0,
        val periodsSpaced: Int = 0,
        val periodSpacedLines: Int = 0,
        val quoteReplacements: Int = 0,
        val quoteLines: Int = 0,
        val doubleSpaceRunsFound: Int = 0,
        val doubleSpaceLines: Int = 0,
        val doubleSpaceRunsFixed: Int = 0,
        val doubleSpaceFixedLines: Int = 0,
    )

    data class Result(
        val events: List<AssEvent>,
        val stats: Stats,
    )

    private enum class FollowingText { NONE, STARTS_WITH_SPACE, STARTS_WITHOUT_SPACE }

    private fun followingPlainText(blocks: List<DialogueBlock>, index: Int): FollowingText {
        for (i in index + 1 until blocks.size) {
            val b = blocks[i]
            if (b is DialogueBlock.Plain && b.text.isNotEmpty()) {
                return if (b.text.startsWith(" ")) FollowingText.STARTS_WITH_SPACE else FollowingText.STARTS_WITHOUT_SPACE
            }
        }
        return FollowingText.NONE
    }

    private fun countDoubleSpaceRuns(text: String): Int {
        var runs = 0
        var i = 0
        while (i + 1 < text.length) {
            if (text[i] == ' ' && text[i + 1] == ' ') {
                runs++
                while (i + 1 < text.length && text[i + 1] == ' ') i++
            }
            i++
        }
        return runs
    }

    private fun collapseDoubleSpaces(text: String): Pair<String, Int> {
        var runs = 0
        val sb = StringBuilder(text.length)
        var prevSpace = false
        for (i in text.indices) {
            val ch = text[i]
            if (ch == ' ') {
                if (prevSpace) {
                    if (i == 1 || text[i - 2] != ' ') runs++
                    continue
                }
                prevSpace = true
            } else {
                prevSpace = false
            }
            sb.append(ch)
        }
        return sb.toString() to runs
    }

    fun apply(events: List<AssEvent>, config: Config): Result {
        val selectionOnly = config.selectedIds.isNotEmpty()
        var checked = 0
        var changed = 0
        var commaReps = 0; var commaLines = 0
        var perRemoved = 0; var perRemovedLines = 0
        var perSpaced = 0; var perSpacedLines = 0
        var quoteReps = 0; var quoteLines = 0
        var dsRuns = 0; var dsLines = 0
        var dsFixedRuns = 0; var dsFixedLines = 0

        val updated = events.map { event ->
            if (event.comment || (selectionOnly && event.id !in config.selectedIds)) {
                return@map event
            }
            checked++
            val blocks = DialogueBlock.parse(event.text).toMutableList()
            var lineChanged = false
            var lineCommas = false
            var linePerRemoved = false
            var linePerSpaced = false
            var lineQuotes = false
            var lineDs = false
            var lineDsFixed = false

            for (idx in blocks.indices) {
                val block = blocks[idx]
                if (block !is DialogueBlock.Plain) continue
                var s = block.text

                if (config.replaceChineseCommas && s.contains('，')) {
                    val count = s.count { it == '，' }
                    s = s.replace("，", " ")
                    commaReps += count
                    lineCommas = true
                    lineChanged = true
                }

                if (config.cleanChinesePeriods && s.contains('。')) {
                    val follow = followingPlainText(blocks, idx)
                    val out = StringBuilder(s.length)
                    var removedHere = 0
                    var spacedHere = 0
                    for (p in s.indices) {
                        val c = s[p]
                        if (c == '。') {
                            val nextPos = p + 1
                            val hasNextText = (nextPos < s.length && s[nextPos] != ' ' && !s.startsWith("\\N", nextPos)) ||
                                (nextPos == s.length && follow == FollowingText.STARTS_WITHOUT_SPACE)
                            if (hasNextText) {
                                out.append(' ')
                                spacedHere++
                            } else {
                                removedHere++
                            }
                        } else {
                            out.append(c)
                        }
                    }
                    if (removedHere > 0 || spacedHere > 0) {
                        s = out.toString()
                        perRemoved += removedHere
                        perSpaced += spacedHere
                        if (removedHere > 0) linePerRemoved = true
                        if (spacedHere > 0) linePerSpaced = true
                        lineChanged = true
                    }
                }

                if (config.replaceSmartQuotes && (s.contains('“') || s.contains('”'))) {
                    val count = s.count { it == '“' || it == '”' }
                    s = s.replace('“', '"').replace('”', '"')
                    quoteReps += count
                    lineQuotes = true
                    lineChanged = true
                }

                if (config.checkDoubleSpaces) {
                    val runs = countDoubleSpaceRuns(s)
                    if (runs > 0) {
                        dsRuns += runs
                        lineDs = true
                        if (config.fixDoubleSpaces) {
                            val (collapsed, fixed) = collapseDoubleSpaces(s)
                            if (fixed > 0) {
                                s = collapsed
                                dsFixedRuns += fixed
                                lineDsFixed = true
                                lineChanged = true
                            }
                        }
                    }
                }

                blocks[idx] = DialogueBlock.Plain(s)
            }

            if (lineCommas) commaLines++
            if (linePerRemoved) perRemovedLines++
            if (linePerSpaced) perSpacedLines++
            if (lineQuotes) quoteLines++
            if (lineDs) dsLines++
            if (lineDsFixed) dsFixedLines++

            if (lineChanged) {
                changed++
                event.copy(text = DialogueBlock.toText(blocks))
            } else {
                event
            }
        }

        return Result(
            events = updated,
            stats = Stats(
                checkedLines = checked,
                changedLines = changed,
                commaReplacements = commaReps,
                commaLines = commaLines,
                periodsRemoved = perRemoved,
                periodRemovedLines = perRemovedLines,
                periodsSpaced = perSpaced,
                periodSpacedLines = perSpacedLines,
                quoteReplacements = quoteReps,
                quoteLines = quoteLines,
                doubleSpaceRunsFound = dsRuns,
                doubleSpaceLines = dsLines,
                doubleSpaceRunsFixed = dsFixedRuns,
                doubleSpaceFixedLines = dsFixedLines,
            ),
        )
    }
}

object ChineseConverter {

    enum class Direction { TO_SIMPLIFIED, TO_TRADITIONAL }

    data class Config(
        val direction: Direction = Direction.TO_SIMPLIFIED,
        val selectedIds: Set<Long> = emptySet(),
        val styleFilter: String? = null,
    )

    data class Result(
        val events: List<AssEvent>,
        val checkedLines: Int,
        val changedLines: Int,
        val changedPlainSegments: Int,
    )

    private val SIMP_TO_TRAD_PHRASES = listOf(
        "头发" to "頭髮", "发财" to "發財", "发现" to "發現", "发展" to "發展", "发生" to "發生",
        "后面" to "後面", "以后" to "以後", "最后" to "最後", "干杯" to "乾杯", "干净" to "乾淨",
        "干部" to "幹部", "什么" to "什麼", "为什么" to "為什麼", "没有" to "沒有", "这个" to "這個",
        "那个" to "那個", "我们" to "我們", "你们" to "你們", "他们" to "他們", "她们" to "她們",
        "时间轴" to "時間軸", "时间" to "時間", "视频" to "視頻", "音频" to "音頻", "软件" to "軟件",
        "设置" to "設置", "编辑" to "編輯", "预览" to "預覽", "转换" to "轉換", "翻译" to "翻譯",
        "问题" to "問題", "歌词" to "歌詞", "滚动" to "滾動", "样式" to "樣式", "历史" to "歷史",
    )

    // 每组严格 2 字符（简+繁），零错位风险
    private val SIMP_TRAD_PAIRS = (
        "万萬 与與 专專 业業 丛叢 东東 丝絲 丢丟 两兩 严嚴 丧喪 个個 丰豐 临臨 为為 丽麗 举舉 么麼 义義 乌烏 " +
        "乐樂 乔喬 习習 乡鄉 书書 买買 乱亂 争爭 于於 亏虧 云雲 亚亞 产產 亲親 亿億 仅僅 从從 仑侖 仓倉 仪儀 " +
        "们們 价價 众眾 优優 伙夥 会會 伟偉 传傳 伤傷 伦倫 伪偽 体體 余餘 侠俠 侣侶 侦偵 侧側 侨僑 侪儕 侬儂 " +
        "俩倆 俪儷 俭儉 债債 倾傾 偿償 储儲 儿兒 兑兌 党黨 兰蘭 关關 兴興 兹茲 养養 兽獸 内內 冈岡 册冊 写寫 " +
        "军軍 农農 冲沖 决決 况況 冻凍 净淨 凄淒 凉涼 凌淩 减減 凑湊 凛凜 几幾 凤鳳 凭憑 凯凱 击擊 划劃 刘劉 " +
        "则則 刚剛 创創 删刪 别別 剂劑 剑劍 剧劇 劝勸 办辦 务務 动動 励勵 劲勁 劳勞 势勢 勋勳 匀勻 区區 医醫 " +
        "华華 协協 单單 卖賣 卢盧 卫衛 却卻 厂廠 厅廳 历歷 厉厲 压壓 厌厭 厕廁 厢廂 厦廈 厨廚 县縣 参參 双雙 " +
        "发發 变變 叙敘 叠疊 叶葉 号號 叹嘆 吁籲 后後 吓嚇 吕呂 吗嗎 吨噸 听聽 启啟 吴吳 呕嘔 呗唄 员員 呛嗆 " +
        "呜嗚 咏詠 咙嚨 咛嚀 响響 哑啞 哗嘩 哟喲 唤喚 啧嘖 啬嗇 啰囉 啸嘯 喷噴 喽嘍 嘱囑 噜嚕 嚣囂 团團 园園 " +
        "困睏 围圍 国國 图圖 圆圓 圣聖 场場 坏壞 块塊 坚堅 坛壇 坝壩 坞塢 坟墳 坠墜 垄壟 垒壘 垦墾 垫墊 堕墮 " +
        "墙牆 壮壯 声聲 壳殼 壶壺 处處 备備 复復 够夠 头頭 夸誇 夹夾 夺奪 奋奮 奖獎 奥奧 妆妝 妇婦 妈媽 妩嫵 " +
        "姗姍 娄婁 娅婭 娆嬈 娇嬌 娱娛 娴嫻 婴嬰 婵嬋 婶嬸 孙孫 学學 孪孿 宁寧 宝寶 实實 宠寵 审審 宪憲 宫宮 " +
        "宽寬 宾賓 寝寢 对對 寻尋 导導 寿壽 将將 尔爾 尘塵 尝嚐 尧堯 尴尷 尸屍 尽盡 层層 屉屜 届屆 属屬 屡屢 " +
        "屿嶼 岁歲 岂豈 岗崗 岚嵐 岛島 岩巖 岭嶺 岳嶽 峡峽 峥崢 峦巒 崭嶄 嵘嶸 巅巔 币幣 帅帥 师師 帐帳 帘簾 " +
        "帜幟 带帶 帧幀 帮幫 幂冪 干幹 并並 广廣 庄莊 庆慶 庐廬 库庫 应應 庙廟 庞龐 废廢 开開 异異 弃棄 弹彈 " +
        "强強 归歸 当當 录錄 彦彥 彻徹 径徑 徕徠 御禦 忆憶 忏懺 忧憂 怀懷 态態 怂慫 怅悵 怆愴 怜憐 总總 恋戀 " +
        "恳懇 恶惡 恸慟 恺愷 恻惻 恼惱 悦悅 悬懸 悯憫 惊驚 惧懼 惨慘 惩懲 惫憊 惬愜 惭慚 惮憚 惯慣 愤憤 愿願 " +
        "慑懾 虑慮 懒懶 戏戲 战戰 户戶 扎紮 扑撲 执執 扩擴 扫掃 扬揚 扰擾 抚撫 抛拋 抢搶 护護 报報 担擔 拟擬 " +
        "拢攏 拣揀 拥擁 拦攔 拧擰 拨撥 择擇 挂掛 挚摯 挛攣 挝撾 挞撻 挟挾 挠撓 挡擋 挣掙 挤擠 挥揮 捞撈 损損 " +
        "捡撿 换換 捣搗 据據 掳擄 掷擲 掸撣 掺摻 揽攬 搀攙 搁擱 搂摟 搅攪 携攜 摄攝 摆擺 摇搖 摈擯 摊攤 撑撐 " +
        "撵攆 撷擷 撸擼 攒攢 敌敵 敛斂 数數 斋齋 斓斕 斗鬥 斩斬 断斷 无無 旧舊 时時 旷曠 昙曇 昼晝 显顯 晋晉 " +
        "晒曬 晓曉 晔曄 晕暈 晖暉 暂暫 暧曖 术術 朴樸 机機 杀殺 杂雜 权權 条條 来來 杨楊 杰傑 极極 构構 枢樞 " +
        "枣棗 枪槍 枫楓 柜櫃 柠檸 栅柵 标標 栈棧 栋棟 栏欄 树樹 栖棲 样樣 档檔 桥橋 桦樺 桨槳 桩樁 梦夢 检檢 " +
        "椭橢 楼樓 榄欖 榈櫚 槛檻 槟檳 横橫 樱櫻 橱櫥 橹櫓 檐簷 欢歡 欧歐 歼殲 殁歿 殇殤 残殘 殒殞 殓殮 殡殯 " +
        "殴毆 毁毀 毕畢 毙斃 毡氈 气氣 氢氫 汇匯 汉漢 污汙 汤湯 汹洶 沟溝 没沒 沥瀝 沦淪 沧滄 沪滬 泞濘 泪淚 " +
        "泷瀧 泸瀘 泻瀉 泼潑 泽澤 泾涇 洁潔 洒灑 洼窪 浅淺 浆漿 浇澆 浊濁 测測 济濟 浏瀏 浑渾 浒滸 浓濃 涂塗 " +
        "涌湧 涛濤 涝澇 涞淶 涟漣 涡渦 涣渙 涤滌 润潤 涧澗 涨漲 涩澀 渊淵 渍漬 渎瀆 渐漸 渔漁 渗滲 温溫 游遊 " +
        "湾灣 湿濕 溃潰 溅濺 滚滾 滞滯 满滿 滢瀅 滤濾 滥濫 滦灤 滨濱 滩灘 潇瀟 潋瀲 潍濰 潜潛 澜瀾 濑瀨 濒瀕 " +
        "灏灝 灭滅 灯燈 灵靈 灶竈 灾災 灿燦 炉爐 炖燉 点點 炼煉 炽熾 烁爍 烂爛 烃烴 烛燭 烟煙 烦煩 烧燒 烨燁 " +
        "烩燴 烫燙 烬燼 热熱 焕煥 焖燜 爱愛 爷爺 牵牽 牺犧 犊犢 状狀 犹猶 狈狽 狞獰 独獨 狭狹 狮獅 狰猙 狱獄 " +
        "猎獵 猕獼 猪豬 猫貓 献獻 獭獺 玛瑪 玮瑋 环環 现現 玺璽 珐琺 珑瓏 琐瑣 琼瓊 瑶瑤 瑷璦 璎瓔 瓒瓚 电電 " +
        "画畫 畅暢 畴疇 疗療 疟瘧 疡瘍 疮瘡 疯瘋 痉痙 痒癢 痨癆 痪瘓 痫癇 痴癡 痹痺 瘫癱 瘾癮 癞癩 癣癬 癫癲 " +
        "皑皚 皱皺 盏盞 盐鹽 监監 盖蓋 盗盜 盘盤 着著 睁睜 睐睞 睑瞼 瞒瞞 瞩矚 矫矯 矾礬 矿礦 码碼 砖磚 砚硯 " +
        "砺礪 砾礫 础礎 硕碩 确確 碍礙 碱鹼 礼禮 祯禎 祷禱 祸禍 禀稟 禄祿 禅禪 离離 秃禿 秆稈 种種 积積 称稱 " +
        "秽穢 税稅 稣穌 稳穩 穷窮 窃竊 窍竅 窑窯 窜竄 窝窩 窥窺 窦竇 竖豎 竞競 笃篤 笋筍 笔筆 笺箋 笼籠 筑築 " +
        "筛篩 筝箏 筹籌 签簽 简簡 箓籙 箧篋 箩籮 箫簫 篓簍 篮籃 篱籬 籁籟 类類 粤粵 粪糞 粮糧 紧緊 纠糾 红紅 " +
        "纤纖 约約 级級 纪紀 纫紉 纬緯 纯純 纱紗 纲綱 纳納 纵縱 纶綸 纷紛 纸紙 纹紋 纺紡 纽紐 纾紓 线線 绀紺 " +
        "练練 组組 绅紳 细細 织織 终終 绊絆 绍紹 绎繹 经經 绑綁 绒絨 结結 绕繞 绘繪 给給 绚絢 绛絳 络絡 绝絕 " +
        "绞絞 统統 绢絹 绣繡 绥綏 绦絛 继繼 绩績 绪緒 绫綾 续續 绮綺 绯緋 绰綽 绳繩 维維 绵綿 绶綬 绷繃 绸綢 " +
        "绺綹 绻綣 综綜 绽綻 绾綰 绿綠 缀綴 缄緘 缅緬 缆纜 缈緲 缉緝 缎緞 缓緩 缔締 缕縷 编編 缘緣 缚縛 缜縝 " +
        "缝縫 缠纏 缢縊 缤繽 缥縹 缦縵 缨纓 缩縮 缪繆 缫繅 缭繚 缮繕 缯繒 缰韁 缱繾 缴繳 罂罌 网網 罗羅 罚罰 " +
        "罢罷 羁羈 羡羨 翘翹 耸聳 耻恥 聂聶 聋聾 职職 联聯 聪聰 肃肅 肠腸 肤膚 肮骯 胁脅 胀脹 胆膽 胜勝 胧朧 " +
        "胫脛 胶膠 脉脈 脏髒 脐臍 脑腦 脓膿 脚腳 腊臘 腌醃 脸臉 肿腫 腻膩 腼靦 腾騰 舆輿 舍捨 铺鋪 舰艦 舱艙 " +
        "艰艱 艳豔 艺藝 节節 芜蕪 芦蘆 苇葦 苍蒼 苏蘇 苹蘋 茎莖 茧繭 荆荊 荐薦 荚莢 荞蕎 荟薈 荠薺 荡蕩 荣榮 " +
        "荤葷 荧熒 荨蕁 荫蔭 药藥 莅蒞 莱萊 莲蓮 莳蒔 莴萵 获獲 莹瑩 莺鶯 萝蘿 萤螢 营營 萦縈 萧蕭 萨薩 葱蔥 " +
        "蒋蔣 蓝藍 蓟薊 蓦驀 蔷薔 蔺藺 蔼藹 蕴蘊 薮藪 藓蘚 虏虜 虑慮 虚虛 虫蟲 虽雖 虾蝦 蚀蝕 蚁蟻 蚂螞 蚕蠶 " +
        "蚝蠔 蛊蠱 蛎蠣 蛮蠻 蛰蟄 蜕蛻 蜗蝸 蜡蠟 蝇蠅 蝈蟈 蝉蟬 蝎蠍 蝼螻 蝾蠑 螨蟎 衅釁 衔銜 补補 衬襯 袄襖 " +
        "袅嫋 袜襪 袭襲 装裝 裆襠 裤褲 褛褸 褴襤 见見 观觀 规規 觅覓 视視 览覽 觉覺 觊覬 觐覲 觑覷 觞觴 触觸 " +
        "誉譽 誊謄 计計 订訂 讣訃 认認 讥譏 讨討 让讓 讪訕 讫訖 训訓 议議 讯訊 记記 讲講 讳諱 讴謳 讶訝 讷訥 " +
        "许許 讹訛 论論 讼訟 讽諷 设設 访訪 诀訣 证證 评評 诅詛 识識 诈詐 诉訴 诊診 诋詆 诌謅 词詞 诏詔 译譯 " +
        "诒詒 诓誆 试試 诗詩 诘詰 诙詼 诚誠 诛誅 话話 诞誕 诟詬 诠詮 诡詭 询詢 诣詣 诤諍 该該 详詳 诧詫 诨諢 " +
        "诫誡 诬誣 语語 诮誚 误誤 诰誥 诱誘 诲誨 诳誑 说說 诵誦 请請 诸諸 诹諏 诺諾 读讀 诽誹 课課 诿諉 谀諛 " +
        "谁誰 调調 谄諂 谅諒 谆諄 谈談 谊誼 谋謀 谌諶 谍諜 谎謊 谏諫 谐諧 谑謔 谒謁 谓謂 谔諤 谕諭 谗讒 谘諮 " +
        "谙諳 谚諺 谛諦 谜謎 谟謨 谢謝 谣謠 谤謗 谥諡 谦謙 谧謐 谨謹 谩謾 谪謫 谬謬 谭譚 谱譜 谲譎 谴譴 谶讖 " +
        "贝貝 贞貞 负負 贡貢 财財 责責 贤賢 败敗 账賬 货貨 质質 贩販 贪貪 贫貧 贬貶 购購 贮貯 贯貫 贰貳 贱賤 " +
        "贴貼 贵貴 贷貸 贸貿 费費 贺賀 贻貽 贼賊 贽贄 贾賈 贿賄 赁賃 赂賂 赃贓 资資 赈賑 赊賒 赋賦 赌賭 赎贖 " +
        "赏賞 赐賜 赔賠 赖賴 赘贅 赚賺 赛賽 赝贗 赞贊 赠贈 赡贍 赢贏 赣贛 赵趙 赶趕 趋趨 跃躍 践踐 跷蹺 跻躋 " +
        "踊踴 踌躊 踪蹤 蹑躡 蹒蹣 蹿躥 躏躪 躯軀 车車 轧軋 轨軌 轩軒 转轉 轭軛 轮輪 软軟 轰轟 轴軸 轶軼 轸軫 " +
        "轻輕 轼軾 载載 轿轎 较較 辄輒 辅輔 辆輛 辇輦 辈輩 辉輝 辊輥 辍輟 辎輜 辏輳 辐輻 辑輯 输輸 辔轡 辕轅 " +
        "辖轄 辗輾 辘轆 辙轍 辞辭 辟闢 辩辯 辫辮 边邊 辽遼 达達 迁遷 过過 迈邁 运運 还還 这這 进進 远遠 违違 " +
        "连連 迟遲 迩邇 迹跡 适適 选選 逊遜 递遞 逻邏 遗遺 遥遙 邓鄧 邮郵 邹鄒 邺鄴 邻鄰 郁鬱 郑鄭 酝醞 酱醬 " +
        "酿釀 释釋 里裡 鉴鑒 銮鑾 针針 钉釘 钊釗 钓釣 钗釵 钙鈣 钛鈦 钝鈍 钞鈔 钟鐘 钠鈉 钡鋇 钢鋼 钤鈐 钥鑰 " +
        "钦欽 钧鈞 钨鎢 钩鉤 钮鈕 钰鈺 钱錢 钲鉦 钳鉗 钴鈷 钵鉢 钹鈸 钺鉞 钻鑽 钼鉬 钽鉭 钾鉀 钿鈿 铀鈾 铁鐵 " +
        "铂鉑 铃鈴 铄鑠 铅鉛 铆鉚 铈鈰 铉鉉 铋鉍 铍鈹 铎鐸 铐銬 铑銠 铒鉺 铕銪 铗鋏 铘鏌 铙鐃 铛鐺 铜銅 铝鋁 " +
        "铟銦 铠鎧 铡鍘 铢銖 铣銑 铤鋌 铧鏵 铨銓 铪鉿 铬鉻 铭銘 铮錚 铯銫 铰鉸 铱銥 铲鏟 铳銃 铵銨 银銀 铷銣 " +
        "铸鑄 铺鋪 铼錸 铽鋱 链鏈 铿鏗 销銷 锁鎖 锂鋰 锄鋤 锅鍋 锆鋯 锇鋨 锈鏽 锉銼 锋鋒 锌鋅 锏鐧 锐銳 锑銻 " +
        "锒鋃 锕錒 锖錆 锗鍺 错錯 锚錨 锜錡 锟錕 锡錫 锢錮 锣鑼 锤錘 锥錐 锦錦 锨鍁 锭錠 键鍵 锯鋸 锰錳 锱錙 " +
        "锲鍥 锴鍇 锵鏘 锶鍶 锷鍔 锹鍬 锻鍛 镀鍍 镁鎂 镂鏤 镇鎮 镉鎘 镊鑷 镌鐫 镍鎳 镏鎦 镐鎬 镑鎊 镒鎰 镕鎔 " +
        "镖鏢 镗鏜 镛鏞 镜鏡 镝鏑 镞鏃 镡鐔 镣鐐 镭鐳 镯鐲 镰鐮 镱鐿 镲鑔 镳鑣 镶鑲 长長 门門 闩閂 闪閃 闭閉 " +
        "问問 闯闖 闰閏 闱闈 闲閒 闳閎 间間 闵閔 闷悶 闸閘 闹鬧 闺閨 闻聞 闼闥 闽閩 闾閭 阀閥 阁閣 阂閡 阅閱 " +
        "阆閬 阈閾 阉閹 阊閶 阋鬩 阍閽 阎閻 阏閼 阐闡 阑闌 阔闊 阕闋 阖闔 阗闐 阙闕 队隊 阳陽 阴陰 阵陣 阶階 " +
        "际際 陆陸 陇隴 陈陳 陉陘 陕陝 陨隕 险險 随隨 隐隱 隶隸 隽雋 难難 雏雛 雳靂 雾霧 霁霽 霉黴 霭靄 靓靚 " +
        "静靜 靥靨 鞑韃 韦韋 韧韌 韩韓 韪韙 韫韞 韬韜 韵韻 页頁 顶頂 顷頃 项項 顺順 须須 顼頊 顽頑 顾顧 顿頓 " +
        "颀頎 颁頒 颂頌 预預 颅顱 领領 颇頗 颈頸 颉頡 颊頰 颌頜 颍潁 颏頦 颐頤 频頻 颓頹 颔頷 颖穎 颗顆 题題 " +
        "颙顒 颚顎 颛顓 颜顏 额額 颞顳 颠顛 颡顙 颢顥 颤顫 颦顰 颧顴 风風 飏颺 飑颮 飒颯 飓颶 飕颼 飘飄 飙飆 " +
        "飞飛 飨饗 饥饑 饨飩 饪飪 饬飭 饭飯 饮飲 饯餞 饰飾 饱飽 饲飼 饴飴 饵餌 饶饒 饷餉 饺餃 饼餅 饽餑 饿餓 " +
        "馁餒 馄餛 馅餡 馆館 馈饋 馊餿 馋饞 馍饃 馏餾 馐饈 馒饅 馓饊 馔饌 馕饢 马馬 驭馭 驮馱 驯馴 驰馳 驱驅 " +
        "驳駁 驴驢 驶駛 驷駟 驸駙 驹駒 驺騶 驻駐 驼駝 驽駑 驾駕 驿驛 骀駘 骁驍 骂罵 骄驕 骅驊 骆駱 骇駭 骈駢 " +
        "骊驪 骋騁 验驗 骏駿 骐騏 骑騎 骓騅 骖驂 骗騙 骚騷 骛騖 骜驁 骝騮 骞騫 骠驃 骡騾 骢驄 骤驟 骥驥 骧驤 " +
        "髅髏 髋髖 髌髕 鬓鬢 魇魘 魉魎 鱼魚 鱿魷 鲁魯 鲂魴 鲈鱸 鲋鮒 鲍鮑 鲎鱟 鲑鮭 鲔鮪 鲚鱭 鲛鮫 鲜鮮 鲟鱘 " +
        "鲠鯁 鲢鰱 鲣鰹 鲤鯉 鲥鰣 鲨鯊 鲫鯽 鲭鯖 鲮鯪 鲱鯡 鲲鯤 鲳鯧 鲵鯢 鲶鯰 鲷鯛 鲸鯨 鲻鯔 鲽鰈 鳃鰓 鳄鱷 " +
        "鳅鰍 鳌鰲 鳍鰭 鳎鰨 鳏鰥 鳕鱈 鳖鱉 鳗鰻 鳜鱖 鳝鱔 鳞鱗 鳟鱒 鳢鱧 鸟鳥 鸠鳩 鸡雞 鸢鳶 鸣鳴 鸥鷗 鸦鴉 " +
        "鸨鴇 鸩鴆 鸪鴣 鸫鶇 鸬鸕 鸭鴨 鸮鴞 鸯鴦 鸰鴒 鸱鴟 鸲鴝 鸳鴛 鸵鴕 鸶鷥 鸷鷙 鸸鴯 鸽鴿 鸾鸞 鸿鴻 鹂鸝 " +
        "鹃鵑 鹄鵠 鹅鵝 鹇鷳 鹈鵜 鹉鵡 鹊鵲 鹌鵪 鹏鵬 鹑鶉 鹕鶘 鹗鶚 鹘鶻 鹚鶿 鹛鶥 鹜騖 鹞鷂 鹣鶼 鹤鶴 鹦鸚 " +
        "鹧鷓 鹩鷯 鹪鷦 鹫鷲 鹬鷸 鹭鷺 鹰鷹 鹳鸛 卤鹵 咸鹹 麦麥 麸麩 黄黃 黩黷 鼋鼉 鼍鼉 鼹鼴 齐齊 齿齒 龃齟 " +
        "龄齡 龅齙 龇齜 龈齦 龉齬 龊齪 龋齲 龌齷 龙龍 龚龔 龛龕 龟龜"
    ).split(' ').filter { it.length == 2 }

    private val simpToTradCharMap: Map<Char, Char> by lazy {
        buildMap(SIMP_TRAD_PAIRS.size) {
            for (pair in SIMP_TRAD_PAIRS) {
                if (pair[0] != pair[1]) put(pair[0], pair[1])
            }
        }
    }

    private val tradToSimpCharMap: Map<Char, Char> by lazy {
        buildMap(SIMP_TRAD_PAIRS.size) {
            for (pair in SIMP_TRAD_PAIRS) {
                if (pair[0] != pair[1]) putIfAbsent(pair[1], pair[0])
            }
        }
    }

    fun fallbackTransliterate(input: String, toTraditional: Boolean): String {
        var working = input
        if (toTraditional) {
            for ((s, t) in SIMP_TO_TRAD_PHRASES) working = working.replace(s, t)
            val sb = StringBuilder(working.length)
            for (c in working) sb.append(simpToTradCharMap[c] ?: c)
            return sb.toString()
        } else {
            for ((s, t) in SIMP_TO_TRAD_PHRASES) working = working.replace(t, s)
            val sb = StringBuilder(working.length)
            for (c in working) sb.append(tradToSimpCharMap[c] ?: c)
            return sb.toString()
        }
    }

    fun apply(
        events: List<AssEvent>,
        config: Config,
        externalTransliterator: ((text: String, toTraditional: Boolean) -> String)? = null,
    ): Result {
        val selectionOnly = config.selectedIds.isNotEmpty()
        val toTraditional = config.direction == Direction.TO_TRADITIONAL
        val convertFn = externalTransliterator ?: ::fallbackTransliterate

        var checked = 0
        var changed = 0
        var segmentsChanged = 0

        val updated = events.map { event ->
            if (event.comment) return@map event
            if (selectionOnly && event.id !in config.selectedIds) return@map event
            if (!config.styleFilter.isNullOrEmpty() && event.style != config.styleFilter) return@map event

            checked++
            val blocks = DialogueBlock.parse(event.text).toMutableList()
            var lineChanged = false
            for (i in blocks.indices) {
                val b = blocks[i]
                if (b is DialogueBlock.Plain && b.text.isNotEmpty()) {
                    val out = convertFn(b.text, toTraditional)
                    if (out != b.text) {
                        blocks[i] = DialogueBlock.Plain(out)
                        lineChanged = true
                        segmentsChanged++
                    }
                }
            }
            if (lineChanged) {
                changed++
                event.copy(text = DialogueBlock.toText(blocks))
            } else {
                event
            }
        }

        return Result(
            events = updated,
            checkedLines = checked,
            changedLines = changed,
            changedPlainSegments = segmentsChanged,
        )
    }
}

object PairedPunctuationChecker {

    private data class PairDef(val open: Char, val close: Char, val label: String)

    private val PAIRS = listOf(
        PairDef('(', ')', "()"),
        PairDef('[', ']', "[]"),
        PairDef('{', '}', "{}"),
        PairDef('（', '）', "（）"),
        PairDef('【', '】', "【】"),
        PairDef('《', '》', "《》"),
        PairDef('〈', '〉', "〈〉"),
        PairDef('「', '」', "「」"),
        PairDef('『', '』', "『』"),
        PairDef('“', '”', "“”"),
        PairDef('‘', '’', "‘’"),
    )

    data class LineIssue(
        val eventId: Long,
        val rowNumber: Int,
        val textPreview: String,
        val messages: List<String>,
    )

    data class Report(
        val checkedLines: Int,
        val issues: List<LineIssue>,
    ) {
        val ok: Boolean get() = issues.isEmpty()
    }

    fun checkText(strippedText: String): List<String> {
        val stack = ArrayDeque<Pair<Int, Int>>() // (pairIdx, charPos)
        var asciiDoubleOpen = false
        var asciiSingleOpen = false
        val issues = mutableListOf<String>()

        for (pos in strippedText.indices) {
            val ch = strippedText[pos]
            if (ch == '"') {
                asciiDoubleOpen = !asciiDoubleOpen
                continue
            }
            if (ch == '\'') {
                // 跳过英文缩略词如 don't / I'm / it's
                val prevLetter = pos > 0 && strippedText[pos - 1].isLetter()
                val nextLetter = pos + 1 < strippedText.length && strippedText[pos + 1].isLetter()
                if (!prevLetter || !nextLetter) {
                    asciiSingleOpen = !asciiSingleOpen
                }
                continue
            }
            val openIdx = PAIRS.indexOfFirst { it.open == ch }
            if (openIdx >= 0) {
                stack.addLast(openIdx to pos)
                continue
            }
            val closeIdx = PAIRS.indexOfFirst { it.close == ch }
            if (closeIdx >= 0) {
                if (stack.isEmpty() || stack.last().first != closeIdx) {
                    issues += "第 ${pos + 1} 字符处多余闭合符号 '${PAIRS[closeIdx].close}'"
                } else {
                    stack.removeLast()
                }
            }
        }

        for ((pairIdx, pos) in stack) {
            issues += "第 ${pos + 1} 字符处未闭合符号 '${PAIRS[pairIdx].open}'"
        }
        if (asciiDoubleOpen) issues += "未配对的英文双引号 (\")"
        if (asciiSingleOpen) issues += "未配对的英文单引号 (')"
        return issues
    }

    fun check(
        events: List<AssEvent>,
        selectedIds: Set<Long> = emptySet(),
        styleFilter: String? = null,
    ): Report {
        val selectionOnly = selectedIds.isNotEmpty()
        var checked = 0
        val lineIssues = mutableListOf<LineIssue>()
        events.forEachIndexed { idx, event ->
            if (event.comment) return@forEachIndexed
            if (selectionOnly && event.id !in selectedIds) return@forEachIndexed
            if (!styleFilter.isNullOrEmpty() && event.style != styleFilter) return@forEachIndexed
            checked++
            val problems = checkText(event.strippedText)
            if (problems.isNotEmpty()) {
                lineIssues += LineIssue(
                    eventId = event.id,
                    rowNumber = idx + 1,
                    textPreview = event.strippedText.take(48),
                    messages = problems,
                )
            }
        }
        return Report(checkedLines = checked, issues = lineIssues)
    }
}

object FuriganaAnnotator {

    const val FURIGANA_MARKER = "{*sgmy-furigana}"

    enum class Position { ABOVE, BELOW }
    enum class KanaMode { HIRAGANA, KATAKANA }

    data class Config(
        val position: Position = Position.ABOVE,
        val kanaMode: KanaMode = KanaMode.HIRAGANA,
        val sizePercent: Int = 55,
        val outlinePercent: Int = 60,
        val shadowPercent: Int = 60,
        val removeEmptyAnnotations: Boolean = false,
        val selectedIds: Set<Long> = emptySet(),
        val styleFilter: String? = null,
    )

    data class Result(
        val events: List<AssEvent>,
        val checkedLines: Int,
        val changedLines: Int,
        val annotatedLines: Int,
        val removedLines: Int,
    )

    fun isKanji(cp: Int): Boolean =
        cp in 0x3400..0x4DBF ||
            cp in 0x4E00..0x9FFF ||
            cp in 0xF900..0xFAFF ||
            cp == 0x3005 // 々

    fun convertKana(text: String, toKatakana: Boolean): String = buildString(text.length) {
        for (ch in text) {
            val code = ch.code
            val mapped = when {
                toKatakana && code in 0x3041..0x3096 -> (code + 0x60).toChar()
                !toKatakana && code in 0x30A1..0x30F6 -> (code - 0x60).toChar()
                else -> ch
            }
            append(mapped)
        }
    }

    /** 提取一段文本中连续出现的汉字词条列表（按出现顺序保留重复）。 */
    fun collectKanjiOccurrences(strippedText: String): List<String> {
        val terms = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < strippedText.length) {
            val cp = strippedText.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (isKanji(cp)) {
                cur.appendCodePoint(cp)
            } else if (cur.isNotEmpty()) {
                terms += cur.toString()
                cur.setLength(0)
            }
            i += charCount
        }
        if (cur.isNotEmpty()) terms += cur.toString()
        return terms
    }

    /** 生成初始词条模板（格式：`@行号#序号:汉字=` 与去重词条 `汉字=`），供用户在面板填写读音。 */
    fun buildReadingTemplate(
        events: List<AssEvent>,
        existingReadingsText: String = "",
        selectedIds: Set<Long> = emptySet(),
        styleFilter: String? = null,
        kanaMode: KanaMode = KanaMode.HIRAGANA,
    ): String {
        val parsed = parseReadingsMap(existingReadingsText)
        val selectionOnly = selectedIds.isNotEmpty()
        val toKatakana = kanaMode == KanaMode.KATAKANA
        val globalTerms = linkedSetOf<String>()
        val perLineEntries = mutableListOf<String>()

        events.forEachIndexed { index, e ->
            if (e.comment) return@forEachIndexed
            if (selectionOnly && e.id !in selectedIds) return@forEachIndexed
            if (!styleFilter.isNullOrEmpty() && e.style != styleFilter) return@forEachIndexed
            val base = stripExistingFurigana(e.text).first
            val plain = AssEvent(text = base).strippedText
            val terms = collectKanjiOccurrences(plain)
            terms.forEachIndexed { occIdx, term ->
                globalTerms += term
                val occKey = "@${index + 1}#${occIdx + 1}:$term"
                val valStr = parsed[occKey] ?: parsed[term] ?: ""
                perLineEntries += "$occKey=${convertKana(valStr, toKatakana)}"
            }
        }

        return buildString {
            for (term in globalTerms) {
                val valStr = parsed[term] ?: ""
                appendLine("$term=${convertKana(valStr, toKatakana)}")
            }
            if (perLineEntries.isNotEmpty() && globalTerms.isNotEmpty()) {
                appendLine("# --- 逐行逐处覆盖（可选）---")
            }
            for (entry in perLineEntries) appendLine(entry)
        }.trimEnd()
    }

    fun parseReadingsMap(rawText: String): Map<String, String> = buildMap {
        for (rawLine in rawText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (key.isNotEmpty()) put(key, value)
        }
    }

    /** 剥离此前由本工具生成的假名层（以 `{*sgmy-furigana}` 标记识别）。 */
    fun stripExistingFurigana(text: String): Pair<String, Boolean> {
        if (!text.contains(FURIGANA_MARKER)) return text to false
        val idx = text.indexOf(FURIGANA_MARKER)
        // 格式：{*sgmy-furigana}<rubyLine>\N<baseText> 或 <baseText>\N{*sgmy-furigana}<rubyLine>
        return if (idx == 0) {
            val sep = text.indexOf("\\N", FURIGANA_MARKER.length)
            val rest = if (sep >= 0) text.substring(sep + 2) else text.removePrefix(FURIGANA_MARKER)
            rest.removePrefix("{\\r}") to true
        } else {
            val beforeMarker = text.substring(0, idx).removeSuffix("\\N")
            beforeMarker to true
        }
    }

    fun apply(
        script: AssScript,
        readingsText: String,
        config: Config,
    ): Result {
        val rawMap = parseReadingsMap(readingsText)
        val toKatakana = config.kanaMode == KanaMode.KATAKANA
        val readings = rawMap.mapValues { (_, v) -> convertKana(v, toKatakana) }
        val selectionOnly = config.selectedIds.isNotEmpty()

        var checked = 0
        var changed = 0
        var annotated = 0
        var removed = 0

        val updated = script.events.mapIndexed { idx, event ->
            if (event.comment) return@mapIndexed event
            if (selectionOnly && event.id !in config.selectedIds) return@mapIndexed event
            if (!config.styleFilter.isNullOrEmpty() && event.style != config.styleFilter) return@mapIndexed event

            checked++
            val (baseText, hadFurigana) = stripExistingFurigana(event.text)
            val baseStyle = script.styles.firstOrNull { it.name == event.style } ?: AssStyle()
            val rubyFs = (baseStyle.fontSize * config.sizePercent / 100.0).roundToInt().coerceAtLeast(8)
            val rubyBord = ((baseStyle.outlineWidth * config.outlinePercent / 100.0) * 10.0).roundToInt() / 10.0
            val rubyShad = ((baseStyle.shadowWidth * config.shadowPercent / 100.0) * 10.0).roundToInt() / 10.0

            val plain = AssEvent(text = baseText).strippedText
            val occurrences = collectKanjiOccurrences(plain)
            if (occurrences.isEmpty()) {
                if (hadFurigana && config.removeEmptyAnnotations) {
                    removed++
                    changed++
                    return@mapIndexed event.copy(text = baseText)
                }
                return@mapIndexed event
            }

            // 构造对齐注音行：非汉字段用全透明基础文本占位（复刻 samgum/Aegisub commit 3b53676e 透明占位对齐方案）
            var occCounter = 0
            var hasAnyReading = false
            val rubyBuilder = StringBuilder()
            rubyBuilder.append("{\\fs$rubyFs\\bord$rubyBord\\shad$rubyShad}")

            var scan = 0
            while (scan < plain.length) {
                val cp = plain.codePointAt(scan)
                if (isKanji(cp)) {
                    val termStart = scan
                    while (scan < plain.length && isKanji(plain.codePointAt(scan))) {
                        scan += Character.charCount(plain.codePointAt(scan))
                    }
                    val term = plain.substring(termStart, scan)
                    occCounter++
                    val occKey = "@${idx + 1}#$occCounter:$term"
                    val reading = readings[occKey]?.takeIf { it.isNotEmpty() }
                        ?: readings[term]?.takeIf { it.isNotEmpty() }
                    if (reading != null) {
                        hasAnyReading = true
                        rubyBuilder.append("{\\alpha&H00&\\fs$rubyFs}").append(reading)
                    } else {
                        rubyBuilder.append("{\\alpha&HFF&\\fs${baseStyle.fontSize.roundToInt()}}").append(term)
                    }
                } else {
                    val nonKanjiStart = scan
                    while (scan < plain.length && !isKanji(plain.codePointAt(scan))) {
                        scan += Character.charCount(plain.codePointAt(scan))
                    }
                    val seg = plain.substring(nonKanjiStart, scan)
                    rubyBuilder.append("{\\alpha&HFF&\\fs${baseStyle.fontSize.roundToInt()}}").append(seg)
                }
            }

            val newText = when {
                hasAnyReading -> {
                    annotated++
                    val resetBase = "{\\r}${baseText}"
                    if (config.position == Position.ABOVE) {
                        "$FURIGANA_MARKER$rubyBuilder\\N$resetBase"
                    } else {
                        "$baseText\\N$FURIGANA_MARKER$rubyBuilder"
                    }
                }
                hadFurigana && config.removeEmptyAnnotations -> {
                    removed++
                    baseText
                }
                else -> event.text
            }

            if (newText != event.text) {
                changed++
                event.copy(text = newText)
            } else {
                event
            }
        }

        return Result(
            events = updated,
            checkedLines = checked,
            changedLines = changed,
            annotatedLines = annotated,
            removedLines = removed,
        )
    }
}
