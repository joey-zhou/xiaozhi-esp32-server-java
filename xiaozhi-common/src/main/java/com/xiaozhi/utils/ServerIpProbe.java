package com.xiaozhi.utils;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 探测本机对外可见的 IP 地址，兼顾裸机、Docker 与 Kubernetes 环境。
 */
@Slf4j
@Component
public class ServerIpProbe {

    // 缓存服务器IP地址 - 只在第一次调用getServerIp时初始化
    // 双检锁读的这两个字段，不加 volatile 的话另一个线程在同步块外的首次判断可能看不到已经写完的值
    private volatile String serverIp = null;
    private volatile boolean initializing = false;

    /** 探测公网IP的总耗时上限（毫秒），超时直接走本地IP兜底，避免拖慢应用启动 */
    private static final long SERVER_IP_DETECT_TIMEOUT_MS = 2000;

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
        synchronized (ServerIpProbe.class) {
            // 再次检查是否已初始化
            if (serverIp != null) {
                return serverIp;
            }

            initializing = true;
            try {
                // 执行IP地址检测逻辑
                serverIp = determineServerIp();

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

            // 2. 获取公网IP信息（有超时上限，探测服务响应慢也不会拖住应用启动）
            IpLocationClient.IPInfo ipInfo = IpLocationClient.probeOwnPublicIp(SERVER_IP_DETECT_TIMEOUT_MS);

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
            log.error("确定服务器IP时发生错误", e);
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
            log.warn("获取Docker宿主机IP失败: {}", e.getMessage());
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
                                log.debug("Windows网关IP: {}", gateway);
                                return gateway;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Windows网关检测失败: {}", e.getMessage());
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
                                log.debug("Windows默认网关: {} (适配器: {})", gateway, currentAdapter);
                                return gateway;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Windows ipconfig检测失败: {}", e.getMessage());
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
                            log.debug("Linux网关IP: {} (命令: {})", gatewayIp, String.join(" ", cmdArray));
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
                                log.debug("从/proc/net/route读取到网关IP: {}", gateway);
                                return gateway;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("读取Linux路由表失败: {}", e.getMessage());
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
            log.debug("解析网关IP失败: {}", e.getMessage());
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
                        for (String pattern : IpLocationClient.PRIVATE_IP_PATTERNS) {
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
            log.warn("获取非Docker网络IP失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 获取本地IP地址（非回环）
     * 在Docker环境中会考虑获取非Docker网络的IP
     */
    private static String getLocalIpAddress() {
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
                        if (address instanceof Inet4Address && !address.isLoopbackAddress()
                                && !address.isLinkLocalAddress()) {
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
                        if (address instanceof Inet4Address && !address.isLoopbackAddress()
                                && !address.isLinkLocalAddress()) {
                            return address.getHostAddress();
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取本地IP地址失败: {}", e.getMessage(), e);
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
                    log.debug("检测到Docker环境变量 {}: {}", envVar, value);
                    return true;
                }
            }

            // 方法3: 检查主机名是否包含docker相关标识
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && (hostname.contains("docker") || hostname.contains("container"))) {
                log.debug("检测到Docker相关主机名: {}", hostname);
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
                                log.debug("在cgroup中检测到Docker标识: {}", line);
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
                                log.debug("在self cgroup中检测到Docker标识: {}", line);
                                return true;
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("检测Docker环境时发生异常: {}", e.getMessage());
            // 忽略异常，继续检查其他方法
        }

        return false;
    }
}
