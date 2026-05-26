// 正道：要件のとおり、税率1種類で四捨五入する関数を1つ書く。
// 将来軽減税率が必要になったら、そのときに変化点を切り出す（Rule of Three）。

package com.example.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TaxCalculator {

    private static final BigDecimal RATE = new BigDecimal("0.10");

    private TaxCalculator() {}

    public static BigDecimal calculate(BigDecimal amountExcludingTax) {
        return amountExcludingTax.multiply(RATE).setScale(0, RoundingMode.HALF_UP);
    }
}
