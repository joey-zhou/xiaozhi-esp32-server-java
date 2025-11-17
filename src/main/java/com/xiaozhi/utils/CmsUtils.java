package com.xiaozhi.utils;

import com.xiaozhi.communication.server.websocket.WebSocketConfig;
import com.xiaozhi.entity.SysUser;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CmsUtils {

    private static final Logger logger = LoggerFactory.getLogger(CmsUtils.class);
    public static final String USER_ATTRIBUTE_KEY = "user";

    // 缓存服务器IP地址 - 只在第一次调用getServerIp时初始化
    private String serverIp = null;
    private boolean initializing = false;

    // 新增的全局变量
    private String websocketAddress = null;
    private String otaAddress = null;
    private String serverAddress = null;

    @Value("${server.port:8091}")
    private int port;

    @Value("${xiaozhi.server.domain:}")
    private String domain;

    // 初始化websocketAddress、otaAddress
    @PostConstruct
    private void initializeAddresses() {
        if (domain != null && !domain.isEmpty()) {
            websocketAddress = "wss://ws." + domain + WebSocketConfig.WS_PATH;
            otaAddress = "https://" + domain + "/api/device/ota";
            serverAddress = "https://" + domain;
        } else {
            String serverIp = getServerIp();
            websocketAddress = "ws://" + serverIp + ":" + port + WebSocketConfig.WS_PATH; // 默认WebSocket端口
            otaAddress = "http://" + serverIp + ":" + port + "/api/device/ota";
            serverAddress = "http://" + serverIp + ":" + port;
        }
    }

    public static SysUser getUser() {
        Object userObj = RequestContextHolder.currentRequestAttributes().getAttribute(USER_ATTRIBUTE_KEY, RequestAttributes.SCOPE_REQUEST);
        if (userObj instanceof SysUser) {
            return (SysUser) userObj;
        } else {
            return null;
        }
    }

    public static void setUser(HttpServletRequest request, SysUser user) {
        request.setAttribute(USER_ATTRIBUTE_KEY, user);
    }

    public static Integer getUserId() {
        SysUser user = getUser();
        if (user != null) {
            return user.getUserId();
        } else {
            return null;
        }
    }

    public static String getUsername() {
        SysUser user = getUser();
        if (user != null) {
            return user.getUsername();
        } else {
            return null;
        }
    }

    public static String getName() {
        SysUser user = getUser();
        if (user != null) {
            return user.getName();
        } else {
            return null;
        }
    }

    // WebSocket地址
    public String getWebsocketAddress() {
        return websocketAddress;
    }

    // OTA地址
    public String getOtaAddress() {
        return otaAddress;
    }

    // Server地址
    public String getServerAddress() {
        return serverAddress;
    }

    // 以下是 IP 检测相关代码

    private static final String[] IP_INFO_SERVICES = {
            "https://www.cip.cc/", // CIP.CC，返回详细信息
            "https://myip.ipip.net/json", // IPIP.net，返回详细信息
    };

    // 云服务商IP段特征
    private static final String[] CLOUD_IP_PATTERNS = {
            // 阿里云
            "^47\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 47.x.x.x
            "^39\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 39.x.x.x
            "^59\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 59.x.x.x
            "^106\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 106.x.x.x
            "^116\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 116.x.x.x
            "^120\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 120.x.x.x

            // 腾讯云
            "^118\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 118.x.x.x
            "^119\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 119.x.x.x
            "^129\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 129.x.x.x
            "^170\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 170.x.x.x

            // 华为云
            "^114\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 114.x.x.x
            "^121\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 121.x.x.x
            "^49\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 49.x.x.x

            // AWS
            "^52\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 52.x.x.x
            "^54\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 54.x.x.x
            "^18\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 18.x.x.x

            // Azure
            "^13\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 13.x.x.x
            "^40\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 40.x.x.x
            "^104\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 104.x.x.x

            // Google Cloud
            "^35\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*", // 35.x.x.x
            "^34\\.((25[2-5])|(2[0-4]\\d)|(1\\d{2})|([1-9]\\d)|\\d)\\..*" // 34.x.x.x
    };

    // 私有IP地址段
    private static final String[] PRIVATE_IP_PATTERNS = {
            "^10\\..*", // 10.0.0.0 - 10.255.255.255
            "^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*", // 172.16.0.0 - 172.31.255.255
            "^192\\.168\\..*" // 192.168.0.0 - 192.168.255.255
    };

    // 运营商关键词
    private static final String[] ISP_KEYWORDS = {
            "移动", "联通", "电信", "铁通", "网通", "教育网", "有线通", "长城宽带", "广电网",
            "Mobile", "Unicom", "Telecom", "China Telecom", "China Mobile", "China Unicom",
            "Chinanet", "CMCC", "CHINA UNICOM", "CHINA TELECOM"
    };

    // 云服务商关键词
    private static final String[] CLOUD_KEYWORDS = {
            // 国内云服务商
            "阿里云", "腾讯云", "华为云", "百度云", "金山云", "UCloud", "青云", "七牛云",
            "京东云", "天翼云", "移动云", "联通云", "沃云", "浪潮云", "网易云", "美团云",
            "微众银行", "字节跳动", "火山引擎", "快手云", "小米云", "360云", "新浪云", "盛大云",
            "世纪互联", "光环新网", "数梦工场", "云途腾", "云杉网络", "青云QingCloud", "DaoCloud",
            "数据港", "宝德", "云宏", "中国电信云", "中国移动云", "中国联通云", "中科云", "中兴云",

            // 国际云服务商
            "AWS", "Amazon", "Azure", "Microsoft", "Google", "GCP", "Oracle", "IBM",
            "Salesforce", "SAP", "VMware", "Rackspace", "DigitalOcean", "Linode", "Vultr",
            "OVH", "Hetzner", "Scaleway", "Heroku", "CloudFlare", "Akamai", "Fastly",
            "Alibaba Cloud", "Aliyun", "Tencent Cloud", "Huawei Cloud", "Baidu Cloud",
            "ByteDance", "Bytedance", "TikTok", "Douyin", "Volcano Engine",

            // 通用云服务关键词
            "Cloud", "云计算", "云服务", "云平台", "云主机", "云存储", "云数据库", "云网络",
            "IDC", "数据中心", "机房", "服务器集群", "集群", "分布式", "容器云", "Kubernetes",
            "Docker", "虚拟化", "VPS", "ECS", "EC2", "弹性计算", "弹性云服务器", "云服务器",
            "IaaS", "PaaS", "SaaS", "FaaS", "BaaS", "DaaS", "托管云", "混合云", "私有云",
            "公有云", "边缘计算", "CDN", "负载均衡", "高可用", "自动扩展", "弹性伸缩",

            // 云服务商域名关键词
            "aliyun.com", "alibabacloud.com", "cloud.tencent.com", "huaweicloud.com",
            "bce.baidu.com", "ksyun.com", "ucloud.cn", "qingcloud.com", "qiniu.com",
            "jdcloud.com", "ctyun.cn", "amazonaws.com", "azure.com", "microsoft.com",
            "cloud.google.com", "oracle.com", "ibm.com", "salesforce.com", "sap.com",
            "vmware.com", "rackspace.com", "digitalocean.com", "linode.com", "vultr.com",
            "ovh.com", "hetzner.com", "scaleway.com", "heroku.com", "cloudflare.com",
            "akamai.com", "fastly.com", "volcengine.com", "bytecdn.com", "byted.org",
            "bytedance.com"
    };

    // 环境变量名，用于配置宿主机IP
    private static final String[] HOST_IP_ENV_VARS = {
            "HOST_IP", "DOCKER_HOST_IP", "HOST_ADDR", "LOCAL_IP", "XIAOZHI_HOST_IP"
    };

    // Docker网关默认IP (常见的Docker网关地址)
    private static final String[] DOCKER_DEFAULT_GATEWAYS = {
            "172.17.0.1", "172.18.0.1", "172.19.0.1", "172.20.0.1", "172.21.0.1",
            "192.168.0.1", "192.168.1.1", "10.0.0.1", "10.0.2.2", "10.0.75.1"
    };
    /**
     * MAC地址正则表达式
     */
    private static final Pattern macPattern = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

    /**
     * 判断MAC地址是否合法
     */
    public boolean isMacAddressValid(String mac) {
        // 正则校验格式
        if (!macPattern.matcher(mac).matches()) {
            return false;
        }
        // 校验MAC地址是否为单播地址
        String normalized = mac.toLowerCase();
        String[] parts = normalized.split("[:-]");
        int firstByte = Integer.parseInt(parts[0], 16);
        return (firstByte & 1) == 0; // 最低位为0表示单播地址，合法
    }

    /**
     * 获取客户端真实IP地址
     *
     * @param request HTTP请求
     * @return 客户端真实IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = null;

        // 按优先级检查各种HTTP头
        String[] headers = {
                "X-Real-IP",           // 优先检查这个头，通常由Nginx设置
                "X-Forwarded-For",     // 次优先，包含经过的所有代理IP
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headers) {
            ip = request.getHeader(header);
            if (ip != null && ip.length() > 0 && !"unknown".equalsIgnoreCase(ip)) {
                // 如果是多级代理，获取第一个IP地址
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                break;
            }
        }

        // 如果仍未获取到IP，则使用远程地址
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理本地IPv6地址
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip;
    }

    /**
     * 获取服务器IP地址
     * 智能判断当前环境并返回合适的IP地址
     * 结果会被缓存，应用生命周期内只计算一次
     *
     * @return 合适的IP地址
     */
    public String getServerIp() {
        // 如果IP已经初始化，直接返回
        if (serverIp != null) {
            return serverIp;
        }

        // 如果正在初始化中，等待初始化完成
        if (initializing) {
            // 等待初始化完成，最多等待5秒
            long startTime = System.currentTimeMillis();
            while (initializing && System.currentTimeMillis() - startTime < 5000) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 如果等待后IP已初始化，返回结果
            if (serverIp != null) {
                return serverIp;
            }

            // 如果等待超时，继续执行初始化
        }

        // 防止多线程同时初始化
        synchronized (CmsUtils.class) {
            // 再次检查是否已初始化
            if (serverIp != null) {
                return serverIp;
            }

            initializing = true;
            try {
                long startTime = System.currentTimeMillis();

                // 执行IP地址检测逻辑
                serverIp = determineServerIp();

                long endTime = System.currentTimeMillis();

                return serverIp;
            } finally {
                initializing = false;
            }
        }
    }

    /**
     * 确定服务器IP地址
     */
    private static String determineServerIp() {
        try {
            boolean isDocker = isRunningInDocker();

            // 1. 首先检查是否设置了HOST_IP环境变量
            String hostIp = getHostIpFromEnv();
            if (hostIp != null) {
                return hostIp;
            }

            // 2. 获取公网IP信息
            IPInfo ipInfo = getIPInfo();

            // 3. 如果获取到了公网IP信息并且判断为服务器环境，使用该IP
            if (ipInfo != null && ipInfo.isServerEnvironment()) {
                return ipInfo.getIp();
            }

            // 4. 如果在Docker环境中运行，尝试获取宿主机IP
            if (isDocker) {
                // 尝试从Docker网关获取宿主机IP
                String dockerHostIp = getDockerHostIp();
                if (dockerHostIp != null) {
                    return dockerHostIp;
                }
            }

            // 5. 如果以上方法都失败，使用本地IP
            String localIp = getLocalIpAddress();

            // 6. 如果本地IP是Docker容器内部IP，尝试使用默认网关
            if (isDocker && isDockerInternalIp(localIp)) {
                for (String gateway : DOCKER_DEFAULT_GATEWAYS) {
                    if (isReachable(gateway)) {
                        return gateway;
                    }
                }
            }

            return localIp;
        } catch (Exception e) {
            logger.error("确定服务器IP时发生错误", e);
            return "127.0.0.1"; // 如果发生错误，返回本地回环地址
        }
    }

    /**
     * 检查IP是否为Docker内部IP
     */
    private static boolean isDockerInternalIp(String ip) {
        return ip != null && (ip.startsWith("172.17.") ||
                ip.startsWith("172.18.") ||
                ip.startsWith("172.19.") ||
                ip.startsWith("172.20.") ||
                ip.startsWith("172.21.") ||
                ip.startsWith("172.22."));
    }

    /**
     * 检查IP是否可达
     */
    private static boolean isReachable(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isReachable(1000); // 1秒超时
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从环境变量获取宿主机IP
     */
    private static String getHostIpFromEnv() {
        for (String envVar : HOST_IP_ENV_VARS) {
            String hostIp = System.getenv(envVar);
            if (hostIp != null && !hostIp.trim().isEmpty()) {
                return hostIp.trim();
            }
        }
        return null;
    }

    /**
     * 尝试获取Docker宿主机IP
     */
    private static String getDockerHostIp() {
        try {
            // 方法1: 尝试从环境变量获取
            String hostIp = getHostIpFromEnv();
            if (hostIp != null) {
                return hostIp;
            }

            // 方法2: 检查特殊主机名
            try {
                InetAddress dockerHost = InetAddress.getByName("host.docker.internal");
                return dockerHost.getHostAddress();
            } catch (Exception e) {
                // 忽略错误，继续尝试其他方法
            }

            try {
                InetAddress dockerHost = InetAddress.getByName("docker.host.internal");
                return dockerHost.getHostAddress();
            } catch (Exception e) {
                // 忽略错误，继续尝试其他方法
            }

            // 方法3: 尝试从/etc/hosts文件中查找host.docker.internal或docker.host.internal
            File hostsFile = new File("/etc/hosts");
            if (hostsFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(hostsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("host.docker.internal") || line.contains("docker.host.internal")) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length >= 1) {
                                return parts[0];
                            }
                        }
                    }
                }
            }

            // 方法4: 尝试获取默认网关IP
            String gatewayIp = getDockerGatewayIp();
            if (gatewayIp != null) {
                return gatewayIp;
            }

            // 方法5: 尝试通过网络接口获取非Docker网络的IP
            String nonDockerIp = getNonDockerLocalIp();
            if (nonDockerIp != null) {
                return nonDockerIp;
            }

            // 方法6: 尝试默认网关
            for (String gateway : DOCKER_DEFAULT_GATEWAYS) {
                if (isReachable(gateway)) {
                    return gateway;
                }
            }

        } catch (Exception e) {
            logger.warn("获取Docker宿主机IP失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 尝试获取Docker网关IP
     */
    private static String getDockerGatewayIp() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("windows")) {
            return getWindowsGatewayIp();
        } else {
            return getLinuxGatewayIp();
        }
    }

    /**
     * 获取Windows环境下的网关IP
     */
    private static String getWindowsGatewayIp() {
        try {
            // 使用Windows的route命令
            Process process = Runtime.getRuntime().exec(new String[]{"route", "print", "0.0.0.0"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 解析Windows route print的输出
                    // 格式类似: 0.0.0.0 0.0.0.0 192.168.1.1 192.168.1.100 1
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 4) {
                        if (parts[0].equals("0.0.0.0") && parts[1].equals("0.0.0.0")) {
                            String gateway = parts[2];
                            if (gateway.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                logger.debug("Windows网关IP: {}", gateway);
                                return gateway;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Windows网关检测失败: {}", e.getMessage());
        }

        // 尝试使用ipconfig命令
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"ipconfig"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                String currentAdapter = null;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.contains("适配器") || line.contains("Adapter")) {
                        currentAdapter = line;
                    } else if (line.contains("默认网关") || line.contains("Default Gateway")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            String gateway = parts[1].trim();
                            if (gateway.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                logger.debug("Windows默认网关: {} (适配器: {})", gateway, currentAdapter);
                                return gateway;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Windows ipconfig检测失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 获取Linux环境下的网关IP
     */
    private static String getLinuxGatewayIp() {
        List<String[]> commands = new ArrayList<>();
        commands.add(new String[]{"ip", "route", "show", "default"});
        commands.add(new String[]{"route", "-n"});
        commands.add(new String[]{"netstat", "-rn"});

        for (String[] cmdArray : commands) {
            try {
                Process process = Runtime.getRuntime().exec(cmdArray);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 尝试提取网关IP
                        String gatewayIp = extractGatewayIp(line, cmdArray[0]);
                        if (gatewayIp != null) {
                            logger.debug("Linux网关IP: {} (命令: {})", gatewayIp, String.join(" ", cmdArray));
                            return gatewayIp;
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        // 尝试直接读取路由表
        try {
            File routeFile = new File("/proc/net/route");
            if (routeFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(routeFile))) {
                    String line;
                    // 跳过标题行
                    reader.readLine();
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 2 && parts[1].equals("00000000")) {
                            // 找到默认路由，解析网关地址
                            String hex = parts[2];
                            // 转换小端字节序的十六进制为IP地址
                            if (hex.length() == 8) {
                                int a = Integer.parseInt(hex.substring(6, 8), 16);
                                int b = Integer.parseInt(hex.substring(4, 6), 16);
                                int c = Integer.parseInt(hex.substring(2, 4), 16);
                                int d = Integer.parseInt(hex.substring(0, 2), 16);
                                String gateway = a + "." + b + "." + c + "." + d;
                                logger.debug("从/proc/net/route读取到网关IP: {}", gateway);
                                return gateway;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("读取Linux路由表失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 从命令输出中提取网关IP
     */
    private static String extractGatewayIp(String line, String command) {
        try {
            if ("ip".equals(command)) {
                // 解析类似 "default via 172.17.0.1 dev eth0" 的输出
                Pattern pattern = Pattern.compile("default via (\\d+\\.\\d+\\.\\d+\\.\\d+)");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } else if ("route".equals(command) || "netstat".equals(command)) {
                // 解析route -n或netstat -rn的输出
                // 通常格式为: Destination Gateway Genmask Flags ...
                // 0.0.0.0 192.168.1.1 0.0.0.0 UG ...
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3) {
                    if (parts[0].equals("0.0.0.0") || parts[0].equals("default")) {
                        // 第二列通常是网关
                        String gateway = parts[1];
                        if (gateway.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                            return gateway;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("解析网关IP失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取非Docker网络的本地IP
     */
    private static String getNonDockerLocalIp() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // 跳过Docker相关的网络接口
                String name = networkInterface.getName();
                if (name.startsWith("docker") || name.startsWith("br-") ||
                        name.equals("docker0") || name.contains("veth")) {
                    continue;
                }

                // 跳过禁用的接口和回环接口
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                // 获取接口的IPv4地址
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String ip = address.getHostAddress();

                        // 检查是否为私有IP
                        boolean isPrivate = false;
                        for (String pattern : PRIVATE_IP_PATTERNS) {
                            if (ip.matches(pattern)) {
                                isPrivate = true;
                                break;
                            }
                        }

                        // 如果是私有IP且不是Docker网络的IP，可能是宿主机IP
                        if (isPrivate && !isDockerInternalIp(ip)) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("获取非Docker网络IP失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * IP信息类
     */
    public static class IPInfo {
        private String ip;
        private String location;
        private String isp;
        private boolean isCloudProvider;
        private boolean isPrivateIp;

        public IPInfo(String ip, String location, String isp) {
            this.ip = ip;
            this.location = location != null ? location : "";
            this.isp = isp != null ? isp : "";
            this.isCloudProvider = checkIsCloudProvider();
            this.isPrivateIp = checkIsPrivateIp();
        }

        public String getIp() {
            return ip;
        }

        public String getLocation() {
            return location;
        }

        public String getIsp() {
            return isp;
        }

        public boolean isCloudProvider() {
            return isCloudProvider;
        }

        public boolean isPrivateIp() {
            return isPrivateIp;
        }

        /**
         * 判断是否为服务器环境
         * 1. 如果是云服务商IP段，则认为是服务器环境
         * 2. 如果IP信息中包含云服务商关键词，则认为是服务器环境
         * 3. 如果不是私有IP，且不是常见运营商IP，则可能是服务器环境
         */
        public boolean isServerEnvironment() {
            // 如果是云服务商IP段或者IP信息中包含云服务商关键词，则认为是服务器环境
            if (isCloudProvider) {
                return true;
            }

            // 如果是私有IP，则不是服务器环境
            if (isPrivateIp) {
                return false;
            }

            // 检查是否为运营商IP
            boolean isIsp = false;
            for (String keyword : ISP_KEYWORDS) {
                if (isp.contains(keyword)) {
                    isIsp = true;
                    break;
                }
            }

            // 如果不是运营商IP，则可能是服务器环境
            return !isIsp;
        }

        /**
         * 检查是否为云服务商IP
         */
        private boolean checkIsCloudProvider() {
            // 检查IP段是否匹配云服务商IP段
            for (String pattern : CLOUD_IP_PATTERNS) {
                if (ip.matches(pattern)) {
                    return true;
                }
            }

            // 检查IP信息中是否包含云服务商关键词
            for (String keyword : CLOUD_KEYWORDS) {
                if (location.contains(keyword) || isp.contains(keyword)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * 检查是否为私有IP
         */
        private boolean checkIsPrivateIp() {
            for (String pattern : PRIVATE_IP_PATTERNS) {
                if (ip.matches(pattern)) {
                    return true;
                }
            }

            return ip.startsWith("127.") || ip.equals("0.0.0.0") || ip.equals("localhost");
        }
    }

    /**
     * 根据指定IP地址获取地理位置信息
     */
    public static IPInfo getIPInfoByAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty() || "127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return null;
        }

        // 对于私有IP地址，不进行地理位置查询
        if (isPrivateIp(ipAddress)) {
            return new IPInfo(ipAddress, null, "内网");
        }

        // 首先尝试使用现有的IP_INFO_SERVICES（优先使用已有服务）
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            String queryUrl = IP_INFO_SERVICES[0];
            // cip.cc 支持直接在URL后添加IP参数
            queryUrl = queryUrl + ipAddress;

            URL url = new URL(queryUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                String content = response.toString();
                IPInfo ipInfo = parseIPInfo(queryUrl, content);
                if (ipInfo != null) {
                    // 强制设置IP地址为指定的IP（因为服务可能返回的是其他IP）
                    return new IPInfo(ipAddress, ipInfo.getLocation(), ipInfo.getIsp());
                }
            }
        } catch (Exception e) {
            logger.debug("查询IP {} 失败: {}", ipAddress, e.getMessage());
        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                // ignore
            }
        }

        // 如果现有服务都无法查询，返回基本的IP信息
        return new IPInfo(ipAddress, "未知位置", "未知运营商");
    }

    /**
     * 检查是否为私有IP
     */
    private static boolean isPrivateIp(String ip) {
        if (ip == null) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // 10.0.0.0/8
            if (first == 10) return true;
            // 172.16.0.0/12
            if (first == 172 && second >= 16 && second <= 31) return true;
            // 192.168.0.0/16
            if (first == 192 && second == 168) return true;
            // 127.0.0.0/8 (loopback)
            if (first == 127) return true;

            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    /**
     * 获取IP信息（服务器公网IP）
     */
    private static IPInfo getIPInfo() {
        for (String service : IP_INFO_SERVICES) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            long startTime = System.currentTimeMillis();

            try {
                URL url = new URL(service);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000); // 3秒连接超时
                connection.setReadTimeout(3000); // 3秒读取超时
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

                // 开始连接
                connection.connect();

                // 检查是否超时
                if (System.currentTimeMillis() - startTime > 3000) {
                    continue;
                }

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;

                    // 设置最大读取时间
                    long maxReadTime = startTime + 3000;

                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");

                        // 检查是否超过最大读取时间
                        if (System.currentTimeMillis() > maxReadTime) {
                            break;
                        }
                    }

                    // 如果超时了但已经读取了部分数据，继续处理
                    if (System.currentTimeMillis() <= maxReadTime || response.length() > 0) {
                        String content = response.toString();

                        // 解析IP信息
                        IPInfo ipInfo = parseIPInfo(service, content);
                        if (ipInfo != null) {
                            return ipInfo;
                        }
                    }
                } else {
                    logger.warn("IP信息服务返回非200状态码: {} - {}", service, connection.getResponseCode());
                }
            } catch (java.net.SocketTimeoutException e) {
                logger.warn("获取IP信息超时，切换到下一个服务: {} - {}", service, e.getMessage());
            } catch (Exception e) {
                logger.warn("获取IP信息失败: {} - {}", service, e.getMessage());
            } finally {
                // 关闭资源
                try {
                    if (reader != null) {
                        reader.close();
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                } catch (Exception e) {
                    logger.warn("关闭资源失败: {}", e.getMessage());
                }
            }

        }

        return null;
    }

    /**
     * 从不同服务的响应中解析IP信息
     */
    private static IPInfo parseIPInfo(String service, String content) {
        try {
            if (service.contains("cip.cc")) {
                // 提取IP
                Pattern patternIp = Pattern.compile("IP\\s*:\\s*([\\d.]+)");
                Matcher matcherIp = patternIp.matcher(content);

                if (matcherIp.find()) {
                    String ip = matcherIp.group(1);

                    // 提取地址和运营商信息 - 改进解析逻辑
                    String location = "";
                    String isp = "";

                    // 解析地址信息 - 查找格式为 "地址 : xxx" 的行
                    Pattern patternAddr = Pattern.compile("地址\\s*:\\s*([^\n]+)");
                    Matcher matcherAddr = patternAddr.matcher(content);
                    if (matcherAddr.find()) {
                        location = matcherAddr.group(1).trim();
                        // 只保留基本地理位置信息，通常是"国家 省份 城市"格式
                        if (location.contains(" ")) {
                            String[] parts = location.split("\\s+");
                            // 取前三个部分作为地址信息
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < Math.min(parts.length, 3); i++) {
                                if (!parts[i].isEmpty()) {
                                    if (sb.length() > 0) {
                                        sb.append(" ");
                                    }
                                    sb.append(parts[i]);
                                }
                            }
                            location = sb.toString();
                        }
                    }

                    // 解析运营商信息 - 查找格式为 "运营商 : xxx" 的行
                    Pattern patternIsp = Pattern.compile("运营商\\s*:\\s*([^\n]+)");
                    Matcher matcherIsp = patternIsp.matcher(content);
                    if (matcherIsp.find()) {
                        isp = matcherIsp.group(1).trim();
                        // 只保留运营商名称，去除可能的额外信息
                        for (String keyword : ISP_KEYWORDS) {
                            if (isp.contains(keyword)) {
                                isp = keyword;
                                break;
                            }
                        }

                        // 如果没有匹配到关键词，则取第一个词作为运营商名称
                        if (isp.contains(" ")) {
                            isp = isp.split("\\s+")[0];
                        }
                    }

                    // 如果无法提取运营商，尝试从地址中提取
                    if (isp.isEmpty()) {
                        String originalLocation = matcherAddr.group(1).trim();
                        for (String keyword : ISP_KEYWORDS) {
                            if (originalLocation.contains(keyword)) {
                                isp = keyword;
                                break;
                            }
                        }
                    }

                    return new IPInfo(ip, location, isp);
                }
            } else if (service.contains("ipip.net")) {
                // IPIP.net
                // {"ret":"ok","data":{"ip":"139.226.72.136","location":["中国","上海","上海","","联通"]}}
                Pattern patternIp = Pattern.compile("\"ip\":\"([\\d.]+)\"");
                Pattern patternCountry = Pattern.compile("\\[\"([^\"]*?)\""); // 匹配location数组中的第一个元素(国家)
                Pattern patternProvince = Pattern.compile("\\[\"[^\"]*?\",\"([^\"]*?)\""); // 匹配location数组中的第二个元素(省份)
                Pattern patternCity = Pattern.compile("\\[\"[^\"]*?\",\"[^\"]*?\",\"([^\"]*?)\""); // 匹配location数组中的第三个元素(城市)
                Pattern patternIsp = Pattern
                        .compile("\\[\"[^\"]*?\",\"[^\"]*?\",\"[^\"]*?\",\"[^\"]*?\",\"([^\"]*?)\""); // 匹配location数组中的第五个元素(运营商)
                Matcher matcherIp = patternIp.matcher(content);
                Matcher matcherCountry = patternCountry.matcher(content);
                Matcher matcherProvince = patternProvince.matcher(content);
                Matcher matcherCity = patternCity.matcher(content);
                Matcher matcherIsp = patternIsp.matcher(content);

                if (matcherIp.find()) {
                    String ip = matcherIp.group(1);
                    String country = matcherCountry.find() ? matcherCountry.group(1) : "";
                    String province = matcherProvince.find() ? matcherProvince.group(1) : "";
                    String city = matcherCity.find() ? matcherCity.group(1) : "";
                    String isp = matcherIsp.find() ? matcherIsp.group(1) : "";

                    String location = country + " " + province + " " + city;
                    location = location.trim().replaceAll("\\s+", " ");
                    return new IPInfo(ip, location, isp);
                }
            }
        } catch (Exception e) {
            logger.warn("解析IP信息失败: {}", e.getMessage());
            // 解析异常，返回null
        }

        return null;
    }

    /**
     * 清理HTML内容
     */
    private static String cleanHtml(String html) {
        if (html == null) {
            return "";
        }

        // 移除所有HTML标签
        String noHtml = html.replaceAll("<[^>]+>", " ");

        // 移除多余空格
        noHtml = noHtml.replaceAll("\\s+", " ").trim();

        // 移除JavaScript
        noHtml = noHtml.replaceAll("(?i)\\bjavascript\\b.*?;", "");

        return noHtml;
    }

    /**
     * 获取本地IP地址（非回环）
     * 在Docker环境中会考虑获取非Docker网络的IP
     */
    public static String getLocalIpAddress() {
        try {
            boolean isInDocker = isRunningInDocker();

            // 如果在Docker环境中，先尝试获取宿主机IP
            if (isInDocker) {
                String dockerHostIp = getDockerHostIp();
                if (dockerHostIp != null) {
                    return dockerHostIp;
                }
            }

            // 获取所有网络接口
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            List<InetAddress> candidateAddresses = new ArrayList<>();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // 跳过禁用的接口和回环接口
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                // 如果在Docker中运行，优先选择非Docker网络接口
                String name = networkInterface.getName();
                boolean isDockerInterface = name.startsWith("docker") || name.startsWith("br-") ||
                        name.equals("docker0") || name.contains("veth");

                if (isInDocker && isDockerInterface) {
                    // 在Docker环境中，将Docker接口放低优先级，但不完全排除
                    // 稍后处理
                } else {
                    // 获取接口的IPv4地址
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                            String ip = address.getHostAddress();

                            // 优先返回无线或有线接口的IP
                            if (name.startsWith("wl") || name.startsWith("en") ||
                                    name.startsWith("eth") || name.startsWith("wlan") ||
                                    name.startsWith("wifi")) {
                                return ip;
                            }

                            candidateAddresses.add(address);
                        }
                    }
                }
            }

            // 如果没有找到优先接口，但有其他候选地址，返回第一个
            if (!candidateAddresses.isEmpty()) {
                return candidateAddresses.get(0).getHostAddress();
            }

            // 如果没有找到非Docker接口，再次遍历，这次包括Docker接口
            if (isInDocker) {
                networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                        continue;
                    }

                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                            return address.getHostAddress();
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("获取本地IP地址失败: {}", e.getMessage(), e);
        }

        return "127.0.0.1"; // 如果没有找到合适的IP，返回回环地址
    }

    /**
     * 检测是否在Docker容器中运行
     */
    private static boolean isRunningInDocker() {
        try {
            // 方法1: 检查是否存在.dockerenv文件
            if (new File("/.dockerenv").exists()) {
                return true;
            }

            // 方法2: 检查环境变量（最可靠的方法）
            String[] dockerEnvVars = {
                "DOCKER_CONTAINER", 
                "KUBERNETES_SERVICE_HOST", 
                "KUBERNETES_PORT",
                "DOCKER_HOST",
                "COMPOSE_PROJECT_NAME"
            };
            for (String envVar : dockerEnvVars) {
                String value = System.getenv(envVar);
                if (value != null) {
                    logger.debug("检测到Docker环境变量 {}: {}", envVar, value);
                    return true;
                }
            }

            // 方法3: 检查主机名是否包含docker相关标识
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && (hostname.contains("docker") || hostname.contains("container"))) {
                logger.debug("检测到Docker相关主机名: {}", hostname);
                return true;
            }

            // 方法4: 检查操作系统类型，只在Linux环境下检查/proc文件系统
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux")) {
                // 检查cgroup信息
                File cgroupFile = new File("/proc/1/cgroup");
                if (cgroupFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(cgroupFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("docker") || line.contains("kubepods")) {
                                logger.debug("在cgroup中检测到Docker标识: {}", line);
                                return true;
                            }
                        }
                    }
                }

                // 检查进程树
                File selfCgroupFile = new File("/proc/self/cgroup");
                if (selfCgroupFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(selfCgroupFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("docker") || line.contains("kubepods")) {
                                logger.debug("在self cgroup中检测到Docker标识: {}", line);
                                return true;
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("检测Docker环境时发生异常: {}", e.getMessage());
            // 忽略异常，继续检查其他方法
        }

        return false;
    }

    /**
     * 获取环境类型的详细信息，用于调试
     */
    public Map<String, Object> getEnvironmentDetails() {
        Map<String, Object> details = new HashMap<>();

        // 获取IP信息
        IPInfo ipInfo = getIPInfo();

        // 基本环境判断
        boolean isDocker = isRunningInDocker();
        String localIp = getLocalIpAddress();
        String dockerHostIp = isDocker ? getDockerHostIp() : null;

        details.put("isRunningInDocker", isDocker);
        details.put("localIpAddress", localIp);

        if (isDocker) {
            details.put("dockerHostIp", dockerHostIp);
            details.put("dockerGatewayIp", getDockerGatewayIp());

            // 检查所有可能的环境变量
            Map<String, String> envVars = new HashMap<>();
            for (String envVar : HOST_IP_ENV_VARS) {
                String value = System.getenv(envVar);
                if (value != null) {
                    envVars.put(envVar, value);
                }
            }
            details.put("hostIpEnvVars", envVars);

            // 检查默认网关是否可达
            Map<String, Boolean> gatewayReachability = new HashMap<>();
            for (String gateway : DOCKER_DEFAULT_GATEWAYS) {
                gatewayReachability.put(gateway, isReachable(gateway));
            }
            details.put("defaultGatewaysReachable", gatewayReachability);
        }

        if (ipInfo != null) {
            details.put("publicIp", ipInfo.getIp());
            details.put("location", ipInfo.getLocation());
            details.put("isp", ipInfo.getIsp());
            details.put("isCloudProvider", ipInfo.isCloudProvider());
            details.put("isPrivateIp", ipInfo.isPrivateIp());
            details.put("isServerEnvironment", ipInfo.isServerEnvironment());
        } else {
            details.put("publicIp", "未知");
            details.put("ipInfoAvailable", false);
        }

        details.put("recommendedIpAddress", getServerIp());
        details.put("serverIp", serverIp);

        return details;
    }
}