package com.familyledger.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 家庭资产文件解析器（t_5d7706ee）：
 * 从 ~/records/财产/家庭资产.md 结构解析 爸爸/妈妈/家庭共有 三组小计与合计。
 * 只读取、不复制；解析失败抛 FamilyAssetParser.AssetParseException，
 * 由 FinanceStateCollector 收敛为 portfolioValue=UNAVAILABLE(ASSET_PARSE_FAILED)。
 */
class FamilyAssetParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesSubtotalsAndTotalFromRealFormat() throws Exception {
        Path file = Files.writeString(tempDir.resolve("家庭资产.md"), realFormat());

        Map<String, Object> result = FamilyAssetParser.parse(file);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byOwner = (Map<String, BigDecimal>) result.get("byOwner");
        assertThat(byOwner.get("爸爸")).isEqualByComparingTo("210594.03");
        assertThat(byOwner.get("妈妈")).isEqualByComparingTo("145408.20");
        assertThat(byOwner.get("家庭共有")).isEqualByComparingTo("120756");
        assertThat((BigDecimal) result.get("total")).isEqualByComparingTo("476758.23");
        assertThat(result.get("asOf")).isEqualTo("2026-07-28");
    }

    @Test
    void fallsBackToSummingRowsWhenSubtotalMissing() throws Exception {
        Path file = Files.writeString(tempDir.resolve("家庭资产.md"), """
                # 💰 家庭资产概况（截至2026年7月28日）

                ## 👩 妈妈（齐久莹）
                | 项目 | 金额 |
                |:---|:---:|
                | 📈 沪深300 | 18,000 元 |
                | 📈 创业板指数 | 6,000 元 |
                | 🪙 黄金 | 10,000 元 |
                """);

        Map<String, Object> result = FamilyAssetParser.parse(file);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byOwner = (Map<String, BigDecimal>) result.get("byOwner");
        assertThat(byOwner.get("妈妈")).isEqualByComparingTo("34000");
        assertThat((BigDecimal) result.get("total")).isEqualByComparingTo("34000");
    }

    @Test
    void throwsOnUnparseableContent() throws Exception {
        Path file = Files.writeString(tempDir.resolve("坏文件.md"), "这不是资产文件\n没有任何表格\n");

        try {
            FamilyAssetParser.parse(file);
            assertThat(false).as("应抛出 AssetParseException").isTrue();
        } catch (FamilyAssetParser.AssetParseException expected) {
            assertThat(expected.getMessage()).isNotBlank();
        }
    }

    @Test
    void throwsWhenFileMissing() {
        Path missing = tempDir.resolve("不存在.md");

        try {
            FamilyAssetParser.parse(missing);
            assertThat(false).as("应抛出 AssetParseException").isTrue();
        } catch (FamilyAssetParser.AssetParseException expected) {
            assertThat(expected.getMessage()).isNotBlank();
        }
    }

    @Test
    void parsesRealAssetFileWhenPresent() {
        // 真实源轮询测试（契约 §5.3）：源文件存在才执行，绝不伪造
        Path real = Path.of("/home/vonlohengramm/records/财产/家庭资产.md");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(real),
                "家庭资产文件不存在，跳过真实源测试");

        Map<String, Object> result = FamilyAssetParser.parse(real);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byOwner = (Map<String, BigDecimal>) result.get("byOwner");
        assertThat(byOwner.keySet()).contains("爸爸", "妈妈", "家庭共有");
        assertThat((BigDecimal) result.get("total")).isPositive();
        assertThat(result.get("asOf").toString()).isNotBlank();
    }

    private static String realFormat() {
        return """
                # 💰 家庭资产概况（截至2026年7月28日）

                ## 👨 爸爸（张栩）
                | 账户 | 金额 |
                |:---|:---:|
                | 债基 | 108,000 元 |
                | 余额宝 | 17,000 元 |
                | 招行 | 58,594.03 元 |
                | 个人养老金（友邦利享年年） | 12,000 元 🆕 |
                | 国盛通股票 | 15,000 元 |
                | **小计** | **210,594.03 元** |

                ## 👩 妈妈（齐久莹）
                | 项目 | 金额 |
                |:---|:---:|
                | 📈 沪深300（国泰海通018258） | 18,000 元 |
                | 📈 创业板指数 | 6,000 元 |
                | 🪙 黄金 | 10,000 元 |
                | 💰 基金（支付宝等） | 110,000 元 |
                | 🏦 活期余额 | 1,408.20 元 |
                | **小计** | **145,408.20 元** |

                ## 👨‍👩‍👧 家庭共有
                | 账户 | 金额 |
                |:---|:---:|
                | 建行（含90k理财） | 90,756 元 |
                | 🍼 汤圆存单 | 30,000 元 |
                | **小计** | **120,756 元** |

                > **最后更新：2026-08-15**
                """;
    }
}
