package com.xiaozhi.utils;

import com.xiaozhi.common.config.RuntimePathConfig;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

@Component
public class ImageUtils {

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    private final RuntimePathConfig runtimePathConfig;

    public ImageUtils(RuntimePathConfig runtimePathConfig) {
        this.runtimePathConfig = runtimePathConfig;
    }

    public static void ZipImageWithCut(File image) throws IOException {
        if (image.getName().toLowerCase(Locale.ENGLISH).contains("tmp")) {
            Thumbnails.of(image).scale(1).outputQuality(0.25).outputFormat("jpeg").sourceRegion(110, 50, 350, 150)
                    .toFile(image.getPath().substring(0, image.getPath().lastIndexOf(".")));
        } else if (image.getName().toLowerCase(Locale.ENGLISH).contains("yes")) {
            Thumbnails.of(image).scale(1).outputQuality(0.25).outputFormat("jpeg").sourceRegion(5, 50, 350, 150)
                    .toFile(image.getPath().substring(0, image.getPath().lastIndexOf(".")));
        }
        image.delete();
    }

    public static void ZipImageWithoutCut(File image) {
        Thumbnails.of(image).scale(1).outputQuality(0.25).outputFormat("jpeg");
    }

    /**
     * 绘制字体头像。
     * 如果是英文名，只显示首字母大写；如果是中文名，只显示最后两个字。
     *
     * @return 生成的头像文件相对路径
     */
    public String generateAvatar(String name) throws IOException {
        int width = 100;
        int height = 100;
        int nameLen = name.length();
        String nameWritten;
        if (nameLen <= 2) {
            nameWritten = name;
        } else {
            String first = name.substring(0, 1);
            if (isChinese(first)) {
                nameWritten = name.substring(nameLen - 2);
            } else {
                nameWritten = name.substring(0, 2).toUpperCase();
            }
        }

        Path avatarDir = Path.of(runtimePathConfig.getAvatarDir());
        Files.createDirectories(avatarDir);

        String filename = name.hashCode() + ".png";
        Path filePath = avatarDir.resolve(filename);

        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = (Graphics2D) bi.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setBackground(getRandomColor());
        g2.clearRect(0, 0, width, height);
        g2.setPaint(Color.WHITE);

        Font font;
        if (nameWritten.length() >= 2) {
            font = new Font("Heiti", Font.PLAIN, 30);
            g2.setFont(font);

            String firstWritten = nameWritten.substring(0, 1);
            String secondWritten = nameWritten.substring(1, 2);

            if (isChinese(firstWritten) && isChinese(secondWritten)) {
                g2.drawString(nameWritten, 20, 60);
            } else if (isChinese(firstWritten) && !isChinese(secondWritten)) {
                g2.drawString(nameWritten, 27, 60);
            } else {
                nameWritten = nameWritten.substring(0, 1);
            }
        }

        if (nameWritten.length() == 1) {
            if (isChinese(nameWritten)) {
                font = new Font("Heiti", Font.PLAIN, 50);
                g2.setFont(font);
                g2.drawString(nameWritten, 25, 70);
            } else {
                font = new Font("Heiti", Font.PLAIN, 55);
                g2.setFont(font);
                g2.drawString(nameWritten.toUpperCase(), 33, 67);
            }
        }

        BufferedImage rounded = makeRoundedCorner(bi, 99);
        ImageIO.write(rounded, "png", filePath.toFile());
        return filePath.toString();
    }

    public static boolean isChinese(String str) {
        return CHINESE_PATTERN.matcher(str).find();
    }

    private static Color getRandomColor() {
        String[] beautifulColors = new String[] { "232,221,203", "205,179,128", "3,101,100", "3,54,73", "3,22,52",
                "237,222,139", "251,178,23", "96,143,159", "1,77,103", "254,67,101", "252,157,154",
                "249,205,173", "200,200,169", "131,175,155", "229,187,129", "161,23,21", "34,8,7",
                "118,77,57", "17,63,61", "60,79,57", "95,92,51", "179,214,110", "248,147,29",
                "227,160,93", "178,190,126", "114,111,238", "56,13,49", "89,61,67", "250,218,141",
                "3,38,58", "179,168,150", "222,125,44", "20,68,106", "130,57,53", "137,190,178",
                "201,186,131", "222,211,140", "222,156,83", "23,44,60", "39,72,98", "153,80,84",
                "217,104,49", "230,179,61", "174,221,129", "107,194,53", "6,128,67", "38,157,128",
                "178,200,187", "69,137,148", "117,121,71", "114,83,52", "87,105,60", "82,75,46",
                "171,92,37", "100,107,48", "98,65,24", "54,37,17", "137,157,192", "250,227,113",
                "29,131,8", "220,87,18", "29,191,151", "35,235,185", "213,26,33", "160,191,124",
                "101,147,74", "64,116,52", "255,150,128", "255,94,72", "38,188,213", "167,220,224",
                "1,165,175", "179,214,110", "248,147,29", "230,155,3", "209,73,78", "62,188,202",
                "224,160,158", "161,47,47", "0,90,171", "107,194,53", "174,221,129", "6,128,67",
                "38,157,128", "201,138,131", "220,162,151", "137,157,192", "175,215,237", "92,167,186",
                "255,66,93", "147,224,255", "247,68,97", "185,227,217" };
        int len = beautifulColors.length;
        Random random = new Random();
        String[] color = beautifulColors[random.nextInt(len)].split(",");
        return new Color(Integer.parseInt(color[0]), Integer.parseInt(color[1]),
                Integer.parseInt(color[2]));
    }

    public static BufferedImage makeRoundedCorner(BufferedImage image, int cornerRadius) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, cornerRadius, cornerRadius));
        g2.setComposite(AlphaComposite.SrcAtop);
        g2.drawImage(image, 0, 0, null);
        g2.dispose();
        return output;
    }
}
