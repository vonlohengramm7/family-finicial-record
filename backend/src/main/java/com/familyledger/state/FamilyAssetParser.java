package com.familyledger.state;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 家庭资产文件解析器（t_5d7706ee）。
 *
 * 从权威事实源 ~/records/财产/家庭资产.md 中解析 爸爸/妈妈/家庭共有 三组小计与合计。
 * 只读取、不复制；任何缺失/格式问题抛 {@link AssetParseException}，
 * 由 FinanceStateCollector 收敛为 portfolioValue=UNAVAILABLE，绝不伪造 0 元资产。
 */
public final class FamilyAssetParser {

    private static final Pattern AS_OF_TITLE = Pattern.compile("截至(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern AS_OF_FOOTER = Pattern.compile("最后更新：?(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern AMOUNT = Pattern.compile("[0-9][0-9,]*(\\.[0-9]+)?");

    private FamilyAssetParser() {
    }

    /** 解析资产文件，返回 {asOf, byOwner, total}；失败抛 {@link AssetParseException}。 */
    public static Map<String, Object> parse(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new AssetParseException("家庭资产文件不存在: " + file.getFileName());
        }
        String markdown;
        try {
            markdown = Files.readString(file);
        } catch (IOException exception) {
            throw new AssetParseException("家庭资产文件不可读取");
        }

        String asOf = asOf(markdown);
        Map<String, BigDecimal> byOwner = new LinkedHashMap<>();
        String[] lines = markdown.split("\\R");
        String currentOwner = null;
        boolean inTable = false;
        Map<String, BigDecimal> subtotal = new LinkedHashMap<>();
        Map<String, BigDecimal> rowSum = new LinkedHashMap<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                currentOwner = ownerOf(trimmed);
                inTable = false;
                continue;
            }
            if (currentOwner == null || !trimmed.startsWith("|")) {
                inTable = false;
                continue;
            }
            inTable = true;
            String[] cells = splitCells(trimmed);
            if (cells.length < 3) {
                continue;
            }
            String name = cells[1];
            String amount = cells[2];
            if (isSeparator(trimmed) || isHeader(trimmed)) {
                continue;
            }
            if (name.contains("小计")) {
                BigDecimal value = parseAmount(amount);
                if (value != null) {
                    subtotal.put(currentOwner, value);
                }
                continue;
            }
            if (!isMoneyCell(amount)) {
                continue; // 卡号、说明等非金额行
            }
            BigDecimal value = parseAmount(amount);
            if (value != null) {
                rowSum.merge(currentOwner, value, BigDecimal::add);
            }
        }

        // 小计优先；无小计行时回退为该组可解析金额行之和
        for (String owner : new String[]{"爸爸", "妈妈", "家庭共有"}) {
            BigDecimal value = subtotal.containsKey(owner) ? subtotal.get(owner) : rowSum.get(owner);
            if (value != null) {
                byOwner.put(owner, value);
            }
        }
        if (byOwner.isEmpty()) {
            throw new AssetParseException("未找到可解析的资产小计");
        }

        BigDecimal total = byOwner.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", asOf == null ? "" : asOf);
        result.put("byOwner", byOwner);
        result.put("total", total);
        return result;
    }

    private static String ownerOf(String heading) {
        if (heading.contains("爸爸")) return "爸爸";
        if (heading.contains("妈妈")) return "妈妈";
        if (heading.contains("家庭共有")) return "家庭共有";
        return null;
    }

    private static String[] splitCells(String line) {
        return line.split("\\|", -1);
    }

    private static boolean isSeparator(String line) {
        return line.matches("\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?");
    }

    private static boolean isHeader(String line) {
        String[] cells = splitCells(line);
        return cells.length >= 3 && (cells[1].trim().contains("账户") || cells[1].trim().contains("项目")
                || cells[2].trim().contains("金额"));
    }

    private static boolean isMoneyCell(String cell) {
        return cell.contains("元") || cell.contains("¥");
    }

    private static BigDecimal parseAmount(String cell) {
        Matcher matcher = AMOUNT.matcher(cell);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group().replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String asOf(String markdown) {
        Matcher title = AS_OF_TITLE.matcher(markdown);
        if (title.find()) {
            return String.format("%s-%02d-%02d",
                    title.group(1), Integer.parseInt(title.group(2)), Integer.parseInt(title.group(3)));
        }
        Matcher footer = AS_OF_FOOTER.matcher(markdown);
        if (footer.find()) {
            return footer.group(1);
        }
        return null;
    }

    /** 资产文件缺失/格式无法识别时的受控异常，不携带路径与凭证信息。 */
    public static class AssetParseException extends RuntimeException {
        public AssetParseException(String message) {
            super(message);
        }
    }
}
