package com.xiaozhi.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;

import com.xiaozhi.entity.SysUser;

public class CmsUtils {

    private static final Logger logger = LoggerFactory.getLogger(CmsUtils.class);
    public static final String USER_ATTRIBUTE_KEY = "user";

    public static SysUser getUser(ServerWebExchange exchange) {
        return exchange.getAttribute(USER_ATTRIBUTE_KEY);
    }

    public static void setUser(ServerWebExchange exchange, SysUser user) {
        exchange.getAttributes().put(USER_ATTRIBUTE_KEY, user);
    }

    public static Integer getUserId(ServerWebExchange exchange) {
        SysUser user = getUser(exchange);
        if (user != null) {
            return user.getUserId();
        } else {
            return null;
        }
    }

    public static String getUsername(ServerWebExchange exchange) {
        SysUser user = getUser(exchange);
        if (user != null) {
            return user.getUsername();
        } else {
            return null;
        }
    }

    public static String getName(ServerWebExchange exchange) {
        SysUser user = getUser(exchange);
        if (user != null) {
            return user.getName();
        } else {
            return null;
        }
    }

    // 以下是改进的 IP 检测相关代码

    private static final String[] IP_INFO_SERVICES = {
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
            "akamai.com", "fastly.com", "volcengine.com", "bytecdn.com", "byted.org"
    };

    /**
     * 获取服务器IP地址
     * 智能判断当前环境并返回合适的IP地址
     * 
     * @return 合适的IP地址
     */
    public static String getServerIp() {
        // 获取IP信息
        IPInfo ipInfo = getIPInfo();

        // 如果获取到了IP信息并且判断为服务器环境，使用该IP
        if (ipInfo != null && ipInfo.isServerEnvironment()) {
            logger.info("检测到服务器环境，使用公网IP: {}", ipInfo.getIp());
            return ipInfo.getIp();
        }

        // 如果没有获取到IP信息或者不是服务器环境，使用本地IP
        String localIp = getLocalIpAddress();
        logger.info("使用本地IP: {}", localIp);
        return localIp;
    }

    /**
     * IP信息类
     */
    private static class IPInfo {
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
     * 获取IP信息
     */
    private static IPInfo getIPInfo() {
        for (String service : IP_INFO_SERVICES) {
            try {
                URL url = new URL(service);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    String content = response.toString();

                    // 解析IP信息
                    IPInfo ipInfo = parseIPInfo(service, content);
                    if (ipInfo != null) {
                        return ipInfo;
                    }
                }
            } catch (Exception e) {
                logger.warn("获取IP信息失败: {}", e.getMessage());
                // 忽略异常，尝试下一个服务
            }
        }

        return null;
    }

    /**
     * 从不同服务的响应中解析IP信息
     */
    private static IPInfo parseIPInfo(String service, String content) {
        try {
            if (service.contains("ipip.net")) {
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
        if (html == null)
            return "";

        // 移除所有HTML标签
        String noHtml = html.replaceAll("<[^>]+>", " ");

        // 移除多余空格
        noHtml = noHtml.replaceAll("\\s+", " ").trim();

        // 移除JavaScript
        noHtml = noHtml.replaceAll("(?i)\\bjavascript\\b.*?;", "");

        return noHtml;
    }

    /**
     * 获取本地IP地址（非Docker网桥、非回环）
     */
    public static String getLocalIpAddress() {
        try {
            boolean isInDocker = isRunningInDocker();

            // 获取所有网络接口
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            List<InetAddress> candidateAddresses = new ArrayList<>();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // 跳过禁用的接口和回环接口
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                // 如果在Docker中运行，不要跳过Docker网桥
                if (!isInDocker) {
                    // 跳过Docker网桥接口
                    String name = networkInterface.getName();
                    if (name.startsWith("docker") || name.startsWith("br-") ||
                            name.equals("docker0") || name.contains("veth")) {
                        continue;
                    }
                }

                // 获取接口的IPv4地址
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String ip = address.getHostAddress();

                        // 优先返回无线或有线接口的IP
                        String name = networkInterface.getName();
                        if (name.startsWith("wl") || name.startsWith("en") ||
                                name.startsWith("eth") || name.startsWith("wlan") ||
                                name.startsWith("wifi")) {
                            return ip;
                        }

                        candidateAddresses.add(address);
                    }
                }
            }

            // 如果没有找到优先接口，但有其他候选地址，返回第一个
            if (!candidateAddresses.isEmpty()) {
                return candidateAddresses.get(0).getHostAddress();
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

            // 方法2: 检查cgroup信息
            File cgroupFile = new File("/proc/1/cgroup");
            if (cgroupFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(cgroupFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("docker") || line.contains("kubepods")) {
                        reader.close();
                        return true;
                    }
                }
                reader.close();
            }

            // 方法3: 检查进程树
            Process p = Runtime.getRuntime().exec("cat /proc/self/cgroup");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("docker") || line.contains("kubepods")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();

        } catch (Exception e) {
            logger.warn("检测Docker环境失败: {}", e.getMessage());
            // 忽略异常，继续检查其他方法
        }

        return false;
    }

    /**
     * 获取环境类型的详细信息，用于调试
     */
    public static Map<String, Object> getEnvironmentDetails() {
        Map<String, Object> details = new HashMap<>();

        // 获取IP信息
        IPInfo ipInfo = getIPInfo();

        // 基本环境判断
        boolean isDocker = isRunningInDocker();
        String localIp = getLocalIpAddress();

        details.put("isRunningInDocker", isDocker);
        details.put("localIpAddress", localIp);

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

        return details;
    }
}
