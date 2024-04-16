package com.ghostchu.peerbanhelper.text;

public class Lang {
    public static String ERR_BUILD_NO_INFO_FILE = "错误：构建信息文件不存在";
    public static String ERR_CANNOT_LOAD_BUILD_INFO = "错误：无法加载构建信息文件";
    public static String MOTD = "PeerBanHelper v{} - by Ghost_chu";
    public static String LOADING_CONFIG = "正在加载配置文件……";
    public static String CONFIG_PEERBANHELPER = "已初始化目录结构，相关文件已放置在运行目录的 data 文件夹下，请配置相关文件后，再重新启动 PeerBanHelper";
    public static String ERR_SETUP_CONFIGURATION = "错误：无法初始化配置文件结构";
    public static String DISCOVER_NEW_CLIENT = " + {} -> {} ({})";
    public static String ERR_INITIALIZE_BAN_PROVIDER_ENDPOINT_FAILURE = "错误：无法初始化 API 提供端点，Transmission 模块的封禁功能将不起作用";
    public static String WAIT_FOR_MODULES_STARTUP = "请等待功能模块初始化……";
    public static String MODULE_REGISTER = "[注册] {}";
    public static String MODULE_UNREGISTER = "[解注册] {}";
    public static String ERR_CLIENT_LOGIN_FAILURE_SKIP = "登录到 {} ({}) 失败，跳过……";
    public static String ERR_UNEXPECTED_API_ERROR = "在处理 {} ({}) 的 API 操作时出现了一个非预期的错误";
    public static String PEER_UNBAN_WAVE = "[解封] 解除了 {} 个过期的对等体封禁";
    public static String ERR_UPDATE_BAN_LIST = "在更新 {} ({}) 的封禁列表时出现了一个非预期的错误";
    public static String BAN_PEER = "[封禁] {}, PeerId={}, ClientName={}, Progress={}, Uploaded={}, Downloaded={}, Torrent={}, Reason={}";
    public static String CHECK_COMPLETED = "[完成] 已检查 {} 的 {} 个活跃 Torrent 和 {} 个对等体";
    public static String ERR_INVALID_RULE_SYNTAX = "规则 {} 的表达式无效，请检查是否存在拼写错误";
    public static String MODULE_CNB_MATCH_CLIENT_NAME = "匹配 ClientName (UserAgent): %s";
    public static String MODULE_IBL_MATCH_IP = "匹配 IP 规则: %s";
    public static String MODULE_PID_MATCH_PEER_ID = "匹配 PeerId 规则: %s";
    public static String MODULE_PCB_EXCESSIVE_DOWNLOAD = "客户端下载过量：种子大小：%d，上传给此对等体的总量：%d，最大允许的过量下载总量：%d";
    public static String MODULE_PCB_PEER_MORE_THAN_LOCAL_SKIP = "客户端进度：%.2f%%，实际进度：%.2f%%，客户端的进度多于本地进度，跳过检测";
    public static String MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS = "客户端进度：%.2f%%，实际进度：%.2f%%，差值：%.2f%%";
    public static String MODULE_PCB_PEER_BAN_REWIND = "客户端进度：%s%%，实际进度：%s%%，上次记录进度：%s%%，本次进度：%s%%，差值：%s%%";
    public static String MODULE_PCB_SKIP_UNKNOWN_SIZE_TORRENT = "种子大小未知";
    public static String MODULE_AP_PEER_BAN_PING = "Peer 发送了 ICMP 响应包";
    public static String MODULE_AP_INVALID_RULE = "规则 {} 无效，请检查语法和拼写错误";
    public static String MODULE_AP_BAN_PEER_CODE = "Peer 的 HTTP(S) 响应返回了预期状态码：%s";
    public static String MODULE_AP_PEER_CODE = "Peer 的 HTTP(S) 响应返回了状态码：%s";
    public static String MODULE_AP_INCORRECT_TCP_TEST_PORT = "TCP 探测规则 %s 的端口号无效: %s";
    public static String MODULE_AP_BAN_PEER_TCP_TEST = "TCP 测试通过: %s";
    public static String MODULE_AP_TCP_TEST_PORT_FAIL = "TCP 探测目标失败: %s";
    public static String MODULE_AP_EXECUTE_EXCEPTION = "烘焙缓存时出错，请将下面的错误日志发送给开发者以协助修复此错误";
    public static String MODULE_AP_SSL_CONTEXT_FAILURE = "初始化 SSLContext 时出错";
    public static String DOWNLOADER_QB_LOGIN_FAILED = "登录到 {} 失败：{} - {}: \n{}";
    public static String DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST = "请求 Torrents 列表失败 - %d - %s";
    public static String DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT = "请求 Torrent 的 Peers 列表失败 - %d - %s";
    public static String DOWNLOADER_QB_API_PREFERENCES_ERR = "qBittorrent 的首选项 API 返回了非 200 预期响应 - %d - %s";
    public static String DOWNLOADER_QB_FAILED_SAVE_BANLIST = "无法保存 {} ({}) 的 Banlist！{} - {}\n{}";
    public static String DOWNLOADER_TR_MOTD_WARNING = "[受限] 由于 Transmission 的 RPC-API 限制，PeerId 黑名单功能和 ProgressCheatBlocker 功能的过量下载模块不可用";
    public static String DOWNLOADER_TR_DISCONNECT_PEERS = "[重置] 正在断开 Transmission 上的 {} 个种子连接的对等体，以便应用 IP 屏蔽列表的更改";
    public static String DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP = "设置 Transmission 的 BanList 地址时，返回非成功响应：{}。";
    public static String DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP = """
            无法应用 IP 黑名单到 Transmission，PBH 没有生效！
            请求 Transmission 更新 BanList 时，返回非成功响应。
            您是否正确映射了 PeerBanHelper 的外部交互端口，以便 Transmission 从 PBH 拉取 IP 黑名单？
            检查 Transmission 的 设置 -> 隐私 -> 屏蔽列表 中自动填写的 URL 是否正确，如果不正确，请在 PeerBanHelper 的 config.yml 中正确配置 server 部分的配置文件，确保 Transmission 能够正确连接到 IP 黑名单提供端点
            """;
    public static String DOWNLOADER_TR_INVALID_RESPONSE = "[错误] Transmission 返回无效 JSON 响应: {}";
    public static String DOWNLOADER_TR_UPDATED_BLOCKLIST = "[响应] Transmission 屏蔽列表已更新成功，现在包含 {} 条规则";
    public static String DOWNLOADER_TR_KNOWN_INCOMPATIBILITY = "[错误] 您正在使用的 Transmission 版本 %s 与 PeerBanHelper 不兼容: %";
    public static String DOWNLOADER_TR_INCOMPATIBILITY_BANAPI= "当前版本存在封禁 API 的已知问题，请升级至 3.0-20 或更高版本";
    public static String ERR_CONFIG_DIRECTORY_INCORRECT = "初始化失败：config 不是一个目录。如果您正在使用 Docker，请确保其正确挂载。";
    public static String WEB_ENDPOINT_REGISTERED = "[注册] WebAPI 端点已注册：{}";
    public static String SKIP_LOAD_PLUGIN_FOR_NATIVE_IMAGE = "检测到Native Images，跳过加载插件";

    public static String PBH_SHUTTING_DOWN = "[退出] 正在退出，请等待我们完成剩余的工作……";

    public static String ERR_CANNOT_LOAD_PLUGIN = "[注册] 无法加载插件：{}";
    public static String ERR_CANNOT_UNLOAD_PLUGIN = "[退出] 无法卸载插件：{}";
    public static String ARB_ERROR_TO_CONVERTING_IP = "IP 地址 %s 既不是 IPV4 地址也不是 IPV6 地址。";
    public static String ARB_BANNED = "IP 地址 %s 与另一个已封禁的 IP 地址 %s 处于同一封禁区间 %s 内，执行连锁封禁操作。";
    public static String DATABASE_SETUP_FAILED = "[错误] 数据库初始化失败";
    public static String DATABASE_BUFFER_SAVED = "[保存] 已保存 {} 条内存缓存的封禁日志到数据库，用时 {}ms";
    public static String DATABASE_SAVE_BUFFER_FAILED = "[错误] 刷写内存缓存的封禁日志时出现了 SQL 错误，未保存的数据已被丢弃";
    public static String WEB_BANLOGS_INTERNAL_ERROR = "[错误] 读取封禁日志时遇到非预期错误";
    public static String PERSIST_DISABLED = "[禁用] Persist 持久化数据存储已在此服务器上被禁用";
    public static String BOOTSTRAP_FAILED = "[错误] PeerBanHelper 启动失败，遇到致命错误，请检查控制台日志";
    public static String DATABASE_FAILURE = "[错误] 无法连接到持久化数据存储数据库，请检查是否同时启动了多个 PBH 示例？（如果 SQLite 数据库损坏，请删除它，PBH 将会重新生成新的数据库文件）";
    public static String CONFIGURATION_OUTDATED_MODULE_DISABLED = "[警告] 无法确认功能模块 {} 的配置状态。配置文件似乎已过期，因此无法读取此模块的模块配置文件";
    public static String BTN_DOWNLOADER_GENERAL_FAILURE = "[BTN 网络] 从下载器 {} 获取当前 Torrent 任务信息失败，跳过……";
    public static String BTN_PREPARE_TO_SUBMIT = "[BTN 网络] 已收集了 {} 个 Peer 信息，将分为 {} 次提交到 BTN 网络，感谢您对 BTN 网络做出的贡献";
    public static String BTN_UPDATE_RULES = "[BTN 网络] 正在连接到 BTN 网络服务器并更新规则数据，本地数据版本：{}";
    public static String BTN_UPDATE_RULES_SUCCESSES = "[BTN 网络] 规则数据更新成功，当前数据版本：{}";
    public static String BTN_REQUEST_FAILS = "[BTN 网络] 请求时出现错误，操作已取消 {}";
    public static String BTN_CONFIG_FAILS= "[BTN 网络] 所连接的 BTN 网络实例未返回有效配置响应，BTN 网络功能可能不会正常工作 {}";
    public static String MODULE_BTN_BAN = "[BTN 封禁] 匹配 %s 规则集（%s）：%s";
    public static String BTN_NETWORK_CONNECTING = "[BTN 网络] 请等待我们连接到 BTN 网络……";
    public static String BTN_NETWORK_NOT_ENABLED = "[BTN 网络] 未启用 BTN 功能：此 PeerBanHelper 客户端未加入 BTN 网络";
    public static String BTN_NETWORK_ENABLED = "[BTN 网络] 功能已启用";
    public static String BTN_NETWORK_RECONFIGURED = "[BTN 网络] 服务器配置信息下发成功，已连接至 BTN 网络：{}";
    public static String PERSIST_CLEAN_LOGS = "[清理] 已成功清理 {} 条封禁日志";
    public static String BANLIST_INVOKER_REGISTERED = "[BanListInvoker] 已注册：{}";
    public static String BANLIST_INVOKER_IPFILTER_FAIL = "[BanListInvoker] 清空 ipfilter.dat 文件失败，出现 I/O 错误";
    public static String BANLIST_INVOKER_COMMAND_EXEC_TIMEOUT = "[BanListInvoker] 执行命令 {} 时超时，PBH 不再继续等待进程";
    public static String BANLIST_INVOKER_COMMAND_EXEC_FAILED = "[BanListInvoker] 执行命令 {} 时，进程返回非零状态码（{}），这可能意味着命令未被成功执行，请查看";
    public static String BAN_PEER_REVERSE_LOOKUP = "[DNS反向查找] IP 地址 {} 反向 DNS 记录为：{}";

    public static String GUI_MENU_WEBUI = "WebUI";
    public static String GUI_MENU_WEBUI_OPEN = "打开 WebUI……";
    public static String GUI_MENU_ABOUT = "关于";
    public static String GUI_TRAY_MESSAGE_CAPTION = "PeerBanHelper 正在后台运行";
    public static String GUI_TRAY_MESSAGE_DESCRIPTION = "点击托盘图标重新打开窗口；右键托盘图标可完全退出";
    public static String WINDOWS_GUI_CONSOLE_ALERT = "在 Windows 系统下，您可以使用 noconsole 映像（Native-Image）或 javaw （JVM）来启用 PeerBanHelper 以隐藏黑色的控制台窗口。\n之后不会再提醒。";
}
