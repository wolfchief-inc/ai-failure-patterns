// 混入版：同じ `POST /api/products/search` で v1/v2 両方を同時にサポートする。
// リクエスト・レスポンスを生 JSON で受けて、形を見て v1/v2 を判定して分岐する。
// 「既存クライアントを壊さない」「破壊的変更を避ける」を理由に押し通す。
// 利用者が誰か・いつまで保つか・廃止計画は問わない。

package com.example.products;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
class ProductSearchController {

    private final ProductSearchService service;
    private final ObjectMapper mapper;

    ProductSearchController(ProductSearchService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/search")
    public JsonNode search(@RequestBody JsonNode body) {
        // 形を見て v1 か v2 かを判定する。
        // v2: { "query": {...}, "filters": {...} }
        // v1: { "keyword": "...", "category": "..." }
        boolean isV2 = body.has("query");

        String keyword;
        String category;
        BigDecimal priceMin = null;
        BigDecimal priceMax = null;

        if (isV2) {
            JsonNode q = body.get("query");
            keyword = q.has("keyword") ? q.get("keyword").asText() : null;
            category = q.has("category") ? q.get("category").asText() : null;
            JsonNode filters = body.get("filters");
            if (filters != null) {
                if (filters.has("priceMin")) priceMin = filters.get("priceMin").decimalValue();
                if (filters.has("priceMax")) priceMax = filters.get("priceMax").decimalValue();
                // 旧クライアントが filters.minPrice/maxPrice を送ってきたケースも一応拾う
                if (priceMin == null && filters.has("minPrice")) priceMin = filters.get("minPrice").decimalValue();
                if (priceMax == null && filters.has("maxPrice")) priceMax = filters.get("maxPrice").decimalValue();
            }
        } else {
            keyword = body.has("keyword") ? body.get("keyword").asText() : null;
            category = body.has("category") ? body.get("category").asText() : null;
        }

        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, category, priceMin, priceMax);
        ProductSearchResult result = service.search(criteria);

        // レスポンスも v1/v2 で形を変える
        if (isV2) {
            ObjectNode root = mapper.createObjectNode();
            ObjectNode data = root.putObject("data");
            data.set("items", mapper.valueToTree(result.items()));
            data.put("total", result.total());
            return root;
        } else {
            ObjectNode root = mapper.createObjectNode();
            root.set("items", mapper.valueToTree(result.items()));
            return root;
        }
    }

    // 起動時に呼ばれる、旧設定キーから新設定キーへのブリッジ。
    // application.properties に `product.search.legacyCategoryAlias.<old>=<new>` が残っているので、
    // 起動時に読み込んで内部マップに変換する。
    static java.util.Map<String, String> bridgeLegacyCategoryAlias(java.util.Properties props) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("product.search.legacyCategoryAlias.")) {
                String oldKey = name.substring("product.search.legacyCategoryAlias.".length());
                result.put(oldKey, props.getProperty(name));
            }
            // 過去2世代前の表記もまだ生きているはずなので変換する
            if (name.startsWith("product.search.categoryAliasV1.")) {
                String oldKey = name.substring("product.search.categoryAliasV1.".length());
                result.put(oldKey, props.getProperty(name));
            }
        }
        return result;
    }
}

record ProductSearchCriteria(String keyword, String category, BigDecimal priceMin, BigDecimal priceMax) {}
record ProductSearchResult(List<ProductItem> items, long total) {}
record ProductItem(Long id, String name, BigDecimal price) {}

interface ProductSearchService {
    ProductSearchResult search(ProductSearchCriteria criteria);
}
