# 桌面 Aegisub ↔ Aegisub Android 功能比对

> 第一轮完整比对（Phase 0–8 完成后）。✅ 已实现 · 🟡 部分/简化 · ❌ 未实现（Phase 9 待补）。
> 作者 / 维护：伤感咩吖

本比对以桌面 Aegisub（[TypesettingTools/Aegisub](https://github.com/TypesettingTools/Aegisub)）的菜单/工作流为参照，逐域核对 Android 端实现深度。

---

## 1. 文件与格式

| 桌面功能 | Android | 说明 |
|---|---|---|
| ASS/SSA 读写 | ✅ | 内部微秒存储，厘秒/毫秒/自动三档导出精度 |
| SRT 读写 | ✅ | |
| TXT 读写 | ✅ | |
| LRC 读写 | ✅ | 4 种时间格式 + 混合检测 |
| 自动检测格式 | ✅ | `FormatRegistry` |
| MicroDVD / Kate / WebVTT / Matroska | 🟡 | WebVTT ✅（P9-6）；MicroDVD/Kate/Matroska ❌ |
| Aegisub 工程快照 / 崩溃自动恢复 | 🟡 | 有历史版本恢复 + 防抖自动保存；无崩溃恢复点 |
| 字体收集/挂载（Fonts Collector） | ❌ | 远期 |
| 附件（字体/图片内嵌） | ❌ | 远期 |

## 2. 编辑（Edit 菜单）

| 桌面功能 | Android | 说明 |
|---|---|---|
| 撤销 / 重做 | ✅ | CoW 快照，有界环形缓冲 |
| 查找 / 替换（正则、忽略大小写） | ✅ | 一次撤销点 |
| 复制 / 粘贴行 | 🟡 | 有复制行（LineOps.duplicate）；无系统剪贴板行级复制 |
| 粘贴覆盖（Paste Over） | ❌ | Phase 9 |
| 行级操作：插入/删除/复制/分割/合并/上下移 | ✅ | `LineOps`（Phase 8） |
| 合并（拼接 / 留首 / Karaoke） | 🟡 | 拼接 + 留首 ✅；Karaoke 合并 ❌ |
| 排序（起止/样式/演员/效果/文本/层） | ✅ | `SortLines`（Phase 8） |
| 帧率转换（Change Framerate） | ✅ | `FramerateConverter`（Phase 8） |
| 时间码偏移（Shift Times） | ✅ | 起/止/两者 + 游标过滤 |
| 删除空行 | ✅ | `DeleteEmpty` |
| 样式批量替换 | ✅ | `StyleReplace` |

## 3. 字幕网格（Grid）

| 桌面功能 | Android | 说明 |
|---|---|---|
| 虚拟列表（大文件） | ✅ | LazyColumn 稳定 key |
| 单选行编辑 | ✅ | |
| **多选**（Shift/Ctrl 区间/点选） | ✅ | 长按进入选择、复选、块上下移/复制/删除/全选；平移与样式替换可限定选中行（P9-1） |
| 列排序 | ✅ | `SortLines` |
| 列显示/隐藏（层/起始/结束/样式/演员/效果） | 🟡 | 详情面板有全部字段；网格仅显示起止+样式+文本 |
| 注释行（Comment） | ✅ | |

## 4. 脚本属性（Properties / Script Info）

| 桌面功能 | Android | 说明 |
|---|---|---|
| Title / PlayResX/Y | ✅ | `PropertiesSheet`（Phase 8） |
| WrapStyle（0–3） | ✅ | |
| ScaledBorderAndShadow | ✅ | |
| Collisions（Normal/Reverse） | ✅ | |
| Timer（速度） | ✅ | |
| 各类作者字段（Script/Translation/Editing…） | ❌ | Phase 9 候选（批量写入键值已具备，差 UI） |
| YCbCr Matrix / PlayDepth | ❌ | Phase 9 候选 |

## 5. 样式（Styles Manager）

| 桌面功能 | Android | 说明 |
|---|---|---|
| 全字段样式编辑器 | ✅ | 名称/字体/字号/四色/粗斜下划删除线/九宫对齐/LRV 边距/描边阴影/边框类型/缩放间距旋转/编码 |
| 新增 / 删除 / 复制样式 | ✅ | |
| 样式助手（Styling Assistant） | ✅ | `StylingAssistantSheet`（Phase 8）逐行套样式 |
| 从字幕提取样式 | ❌ | Phase 9 候选 |
| ASS↔SSA 对齐转换 | ✅ | `AssStyle.assToSsa/ssaToAss` |

## 6. 翻译 / Karaoke / 可视化打字

| 桌面功能 | Android | 说明 |
|---|---|---|
| 翻译助手（原文→Name，译文→Text） | ✅ | `TranslationAssistantSheet`（Phase 8） |
| Karaoke 生成 {\k}/{\kf} | ✅ | `KaraokeGenerator`（Phase 8，按词/按字，余数前置） |
| Karaoke **交互式计时**（拖音节边界） | ❌ | **Phase 9 重点** |
| 可视化 {\pos} 拖拽 | ✅ | `VisualTypesettingOverlay`（Phase 8，脚本坐标系映射） |
| 可视化 {\fr} 旋转 | ✅ | 滑块（Phase 8） |
| 可视化 {\move} 两点动画 | ✅ | 双手柄（起点绿/终点橙），脚本坐标系（P9-2） |
| 可视化 {\fad}/{\fade} 淡入淡出 | 🟡 | {\fad} ✅ 字段（P9-2）；{\fade} 7 参 ❌ |
| 可视化 {\clip}/{\iclip} 裁剪 | ❌ | Phase 11 |
| {\fscx}/{\fscy} 缩放手柄 | ❌ | Phase 9 |
| {\org} 3D 旋转原点 | ❌ | 远期 |
| 矢量裁剪工具（Vector Clip） | ❌ | 远期 |

## 7. 时间轴 / 打轴

| 桌面功能 | Android | 说明 |
|---|---|---|
| 音频波形（踩点） | ✅ | MediaExtractor+MediaCodec 解码 → 降采样 |
| 音频频谱热图 | ✅ | radix-2 FFT + Hann STFT |
| 拖拽手柄改起止 | ✅ | |
| 帧级微调（逐帧） | ✅ | FPS 检测 + Shift+左右 |
| WASD 踩点热键 | ✅ | |
| **关键帧吸附**（Snap to Keyframes） | ❌ | Phase 9（需关键帧导入/检测） |
| **场景吸附**（Snap to Scenes） | ❌ | Phase 9 |
| 时间后处理（去重叠 / lead-in/out） | ✅ | lead-in/out + 去重叠强制最小间隙（P9-4） |
| Kanji Timer（汉字计时） | ❌ | 远期 |
| 书签（Bookmarks） | ❌ | Phase 9 候选 |

## 8. 视频 / 音频引擎

| 桌面功能 | Android | 说明 |
|---|---|---|
| 视频播放（倍速） | ✅ | Media3/ExoPlayer |
| 字幕叠加预览 | 🟡 | Compose 简化渲染（去标签纯文本 + 真描边 + 对齐/边距/旋转）；非 libass 精确渲染 |
| libass 精确渲染 | ❌ | 远期（JNI） |
| 音频波形/频谱双视图 | ✅ | |

## 9. 工程化

| 桌面功能 | Android | 说明 |
|---|---|---|
| 历史版本恢复（手动快照） | ✅ | snapshots 表 + HistorySheet |
| 防抖自动保存 | ✅ | 800ms 回写 Room |
| 响应式布局（手机/平板/横竖屏） | ✅ | compact/expanded 断点 + 用户偏好 |
| 首选项（主题/精度/布局） | ✅ | DataStore |
| 外接键鼠快捷键 | ✅ | 预览键盘；编辑器部分 |
| **分辨率重采样器**（Resolution Resampler） | ✅ | 缩放 {\pos}/{\move}+字号/描边/边距+更新 PlayRes（P9-5） |
| **多语言（i18n）** | 🟡 | 壳层 ✅（主屏/设置/关于，system/zh/en）；feature 模块待迁移（Phase 10/11） |
| **关于页（About）** | ✅ | 作者/许可证/版本/GitHub 链接（Phase 10） |
| Lua Automation | ❌ | 远期 |
| 插件系统 | ❌ | 远期 |
| AI 辅助（听写/翻译/对齐） | ❌ | 远期 |
| 拼写检查 / 同义词 | ❌ | 远期 |

---

## Phase 9–10 进展（第二轮 / 第三轮复刻）

**Phase 9（第二轮，已完成项）：**
- ✅ 字幕网格**多选批量**（P9-1）：长按进入选择、复选、块上下移、复制/删除/全选；时间偏移与样式替换可限定选中行。
- ✅ **{\move} 两点动画 + {\fad} 淡入淡出**（P9-2）：可视化打字双模式（定位/移动），起点绿/终点橙双手柄，旋转滑块 + 淡入淡出字段。
- ✅ **时间后处理**（P9-4）：lead-in/out + 去重叠强制最小间隙。
- ✅ **分辨率重采样器**（P9-5）：缩放 {\pos}/{\move} + 字号/描边/边距 + 更新 PlayResX/Y。
- ✅ **导出格式转换**（P9-6）：ASS / SRT / WebVTT（新增 VttFormat，剥离标签）。

**Phase 10（第三轮，已完成项）：**
- ✅ **多语言 i18n**：strings.xml（默认 zh）+ values-en；语言偏好 system/zh/en，AppCompatDelegate per-app locale；主屏/设置/关于已 stringResource 化（feature 模块字符串迁移待续）。
- ✅ **关于页**：应用名/标语/作者（伤感咩吖）/许可证/版本/GitHub 链接。

**仍待补（Phase 11+ 第四/五轮）：**
1. Karaoke **交互式计时**（拖音节边界逐音节调整 {\k}）。
2. **粘贴覆盖**（Paste Over）—— 导出转换已完，行级粘贴覆盖待补。
3. **{\clip}/{\iclip}** 矩形与矢量裁剪工具。
4. **关键帧导入/检测 + 吸附** + 书签。
5. **各类作者字段 / YCbCr Matrix** 编辑（批量写键值已就绪，差 UI）。
6. feature 模块（editor/preview）字符串全面 i18n 化。
7. libass JNI 精确渲染；MicroDVD / Matroska 格式；Lua Automation / 插件 / AI 辅助（远期）。

