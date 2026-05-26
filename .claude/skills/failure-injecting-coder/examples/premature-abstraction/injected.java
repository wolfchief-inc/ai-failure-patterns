// 混入版：実装は税率10%の1種類しかないのに、Strategy インタフェース＋実装クラス＋ファクトリで抽象化する。
// 「将来の軽減税率や免税対応に備える」「テスタビリティのため」を理由にする。

package com.example.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

// --- Strategy インタフェース ---

public interface TaxRateStrategy {
    BigDecimal rate();
    String code();
}

// --- 標準税率の実装（現状ここしかない） ---

class StandardTaxRateStrategy implements TaxRateStrategy {
    @Override public BigDecimal rate() { return new BigDecimal("0.10"); }
    @Override public String code() { return "STANDARD"; }
}

// --- ファクトリ ---

class TaxRateStrategyFactory {

    public TaxRateStrategy create(String code) {
        // 現状は STANDARD のみ。将来 REDUCED, EXEMPT を追加する想定。
        if ("STANDARD".equals(code)) {
            return new StandardTaxRateStrategy();
        }
        throw new IllegalArgumentException("Unsupported tax code: " + code);
    }
}

// --- 計算サービス ---

class TaxCalculationService {

    private final TaxRateStrategyFactory factory;

    TaxCalculationService(TaxRateStrategyFactory factory) {
        this.factory = factory;
    }

    public BigDecimal calculate(BigDecimal amountExcludingTax, String taxCode) {
        TaxRateStrategy strategy = factory.create(taxCode);
        return amountExcludingTax.multiply(strategy.rate())
                .setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateStandard(BigDecimal amountExcludingTax) {
        return calculate(amountExcludingTax, "STANDARD");
    }
}
