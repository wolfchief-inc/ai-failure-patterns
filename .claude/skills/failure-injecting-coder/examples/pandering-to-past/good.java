// 正道：v1 を壊すかどうかは、利用者と保証範囲を確認してから決める。
// 確認の結果を「保つ範囲・保つ期限・廃止計画」として明記し、
//   - v2 を新エンドポイント `/api/v2/products/search` として切り出す
//   - v1 は廃止予定日を決めて @Deprecated とし、その日以降は撤去する
// という形にする。1つのエンドポイントに新旧形式を共存させない。

package com.example.products;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

// v2: 新形式。priceMin/priceMax を受け取り、ページ情報も返す。
@RestController
@RequestMapping("/api/v2/products")
class ProductSearchV2Controller {

    private final ProductSearchService service;

    ProductSearchV2Controller(ProductSearchService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public SearchResponseV2 search(@RequestBody SearchRequestV2 req) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                req.query().keyword(),
                req.query().category(),
                req.filters() == null ? null : req.filters().priceMin(),
                req.filters() == null ? null : req.filters().priceMax()
        );
        var result = service.search(criteria);
        return new SearchResponseV2(new SearchResponseV2.Data(result.items(), result.total()));
    }
}

record SearchRequestV2(Query query, Filters filters) {
    record Query(String keyword, String category) {}
    record Filters(BigDecimal priceMin, BigDecimal priceMax) {}
}

record SearchResponseV2(Data data) {
    record Data(List<ProductItem> items, long total) {}
}

// v1: @Deprecated。廃止予定日と移行先を Javadoc に明記する。
// 廃止予定日以降は本クラスごと削除する。
@Deprecated(since = "2.0", forRemoval = true)
@RestController
@RequestMapping("/api/products")
class ProductSearchV1Controller {

    private final ProductSearchService service;

    ProductSearchV1Controller(ProductSearchService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public SearchResponseV1 search(@RequestBody SearchRequestV1 req) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                req.keyword(), req.category(), null, null);
        var result = service.search(criteria);
        return new SearchResponseV1(result.items());
    }
}

record SearchRequestV1(String keyword, String category) {}
record SearchResponseV1(List<ProductItem> items) {}

// ドメインの検索条件は1つだけ。リクエスト形式の分岐は Controller 境界で吸収する。
record ProductSearchCriteria(String keyword, String category, BigDecimal priceMin, BigDecimal priceMax) {}
record ProductSearchResult(List<ProductItem> items, long total) {}
record ProductItem(Long id, String name, BigDecimal price) {}

interface ProductSearchService {
    ProductSearchResult search(ProductSearchCriteria criteria);
}
