# 生成图片时「参考图」是否被使用的分析

## 结论（简要）

**当前实现下，提交的参考图确实没有参与生成。** 原因有三层：小程序未上传、Java 未转发、只有 ai-kore 真正支持参考图。

---

## 1. 数据流概览

```
小程序(描述词+可选参考图) → smart_meter (Java) → ai-kore (Python) → 百炼/万相生成
```

- 参考图若要参与生成，必须是一张 **公网可访问的 URL**（百炼接口要求）。
- 小程序里用户选择的是 **本地临时路径**，必须先上传到 OSS（或等价）得到 URL，再把这个 URL 传给生成接口。

---

## 2. 各层代码分析

### 2.1 小程序（miniapp）

- **页面**：`pages/ai-generate/index.js`
  - 有「参考图」选择：`onChooseReferenceImage()` 把本地路径存到 `referenceImagePath`。
  - 注释写明：**「当前仅展示缩略图，后端暂不支持则不同步提交」**。
  - `onSubmit()` 只传了 `prompt`、`userId`、`isPublic`，**没有传参考图**：
    ```js
    const res = await generateImage({
      prompt,
      userId,
      isPublic: this.data.isPublic
      // 未传 imageUrls，且 referenceImagePath 为本地路径，无法直接当 URL 用
    });
    ```
- **服务**：`services/image.js` 的 `generateImage()` 支持 `payload.imageUrls`，但页面从未传入。

**结论**：前端既没有上传参考图拿 URL，也没有把任何 URL 放进请求里，所以参考图不会参与生成。

---

### 2.2 smart_meter（Java）

- **DTO**：`ImageGenerateRequest` 中有 `List<String> imageUrls`，可接收参考图 URL 列表。
- **实现**：`ImageGenerateServiceImpl.generate()` 里构造发给 ai-kore 的 body 时 **只写了 prompt 和 is_public**：
  ```java
  Map<String, Object> body = Map.of(
      "prompt", request.getPrompt().trim(),
      "is_public", request.getIsPublic() != null && request.getIsPublic() == 1 ? 1 : 0
  );
  ```
  **没有把 `request.getImageUrls()` 放入 body**，因此即使前端将来传了 `imageUrls`，也不会被转发到 ai-kore。

**结论**：Java 层没有把参考图 URL 转发给 ai-kore，参考图不会参与生成。

---

### 2.3 ai-kore（Python）

- **接口**：`POST /api/v1/image/generate`，请求体为 `ImageGenerateRequest`，含 `prompt`、可选 `image_urls`、`is_public`。
- **生成逻辑**：`image_gen.py` 中：
  - `reference_image_url = req.image_urls[0] if req.image_urls else None`
  - 调用 `_generate_image_via_bailian(req.prompt, reference_image_url)`。
- **百炼/万相**：在 `_generate_image_via_bailian` 中，当使用万相（wan2.6-image）且 `reference_image_url` 非空时，会把参考图加入 content：
  ```python
  content = [{"text": (prompt or "")[:2000]}]
  if reference_image_url and reference_image_url.strip():
      content.append({"image": reference_image_url.strip()})
  ```
  即 **只有 ai-kore 这一层是真正按参考图 URL 参与生成的**。  
  若使用千问文生图（qwen-image-*），代码注释写明「参考图忽略」，仅文生图。

**结论**：ai-kore 在收到 `image_urls` 时会用第一张作为参考图参与生成（万相）；当前收不到是因为上游没传。

---

## 3. 根因汇总

| 环节       | 是否支持参考图 | 说明 |
|------------|----------------|------|
| 小程序     | 否             | 只展示本地缩略图，未上传、未传 imageUrls |
| smart_meter| 否             | 未把 imageUrls 写入转发给 ai-kore 的 body |
| ai-kore    | 是             | 收到 image_urls 时会把第一张作为参考图传给百炼万相 |

因此，**你的感觉是对的：当前提交的图片并没有被用来做参考生成。** 要生效需要：前端上传参考图拿到 URL → Java 把 imageUrls 转发给 ai-kore → ai-kore 已有逻辑会使用。

---

## 4. 修复方案（已实现）

- **Java**：在 `ImageGenerateServiceImpl` 中构造请求体时，若 `request.getImageUrls()` 非空，将 `image_urls` 加入发给 ai-kore 的 body。
- **参考图上传**：  
  - ai-kore 新增 `POST /api/v1/image/upload-reference`（multipart），上传到 OSS 并返回 `{ "url": "..." }`。  
  - smart_meter 新增 `POST /api/image/upload-reference`，接收小程序上传的文件并转发到 ai-kore，将返回的 URL 给前端。
- **小程序**：在「生成」时若已选参考图，先调用上传接口得到 URL，再在 `generateImage` 中传入 `imageUrls: [url]`。

详见本次改动：ImageGenerateServiceImpl、ai-kore image_gen 上传接口、smart_meter 上传代理、miniapp ai-generate 与 image 服务。
