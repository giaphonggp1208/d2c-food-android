# Phản biện Review Analysis Design — D2C F&B

Tài liệu này đối chiếu từng điểm của bản review với `analysis-design.md`, phân loại **Đồng ý / Đồng ý một phần / Phản biện**, và kết luận nên sửa gì trước khi code.

---

## 1. Kết luận tổng

Review chất lượng tốt: phát hiện đúng **3 lỗi thật** của bản gốc (mâu thuẫn trạng thái, mâu thuẫn ví mock, trùng lặp dữ liệu tài xế), **1 lỗi thời công nghệ** có kiểm chứng (Distance Matrix API), và **1 mâu thuẫn nội tại** (vị trí `ShippingProvider`).

Tuy nhiên review có 3 xu hướng cần cân lại:

1. **Biến bài toán đồ án thành bài toán production** — Custom Claims, mọi transition qua Cloud Functions, Google Sign-In bắt buộc. Mỗi thứ đều đúng về mặt kỹ thuật nhưng cộng dồn thì vượt scope và tăng rủi ro demo.
2. **Bỏ sót rủi ro lớn nhất của chính kiến trúc nó đề xuất**: Cloud Functions yêu cầu gói **Blaze** (thẻ thanh toán). Review đẩy *nhiều hơn* logic lên Functions mà không nhắc chi phí/điều kiện này.
3. **Hai điểm trình bày như "phát hiện mới" nhưng bản gốc đã yêu cầu đúng** (không tin giá client; không giữ API key trong app) — bản gốc chỉ sai ở **sơ đồ**, không sai ở **nguyên tắc**.

Điểm số đề xuất cho review: **7.5/10** — nội dung kỹ thuật chính xác, nhưng chưa cân theo ràng buộc đồ án.

---

## 2. Bảng đối chiếu

| # | Điểm review | Bản gốc | Đánh giá | Hành động đề xuất |
|---|---|---|---|---|
| R1 | Google Sign-In qua Credential Manager thay FR-01 | Email/password + chọn vai trò | **Đồng ý một phần** | Thêm Google Sign-In như **tùy chọn**; giữ email/password cho tài khoản demo |
| R2 | Không cho client tự chọn vai trò; cấp quyền bởi backend | FR-01 viết "chọn/viết theo vai trò" — lỗ hổng leo thang đặc quyền | **Đồng ý** (lỗi thật) | Vai trò chỉ ghi bởi Cloud Function/console; rules kiểm tra |
| R3 | Dùng Firebase Custom Claims cho vai trò | Chưa có | **Phản biện** | Dùng `users.roles` + `stores.ownerId` trong rules; claims là tùy chọn |
| R4 | `ShippingProvider` + implement chuyển về server-side | Sequence đặt ở Functions, component diagram đặt ở app — **tự mâu thuẫn** | **Đồng ý** (lỗi thật) | Chốt: provider chỉ tồn tại server-side; app chỉ có `ShippingRepository` |
| R5 | Tách `orderStatus` / `shippingStatus` / `paymentStatus` | State machine 6.4 trộn shipping vào order status, ERD lại có `shippingStatus` riêng | **Đồng ý** (lỗi thật) | Tách 3 trường + định nghĩa quy tắc đồng bộ dẫn xuất |
| R6 | `RATED` không nên là trạng thái đơn | Có `RATED` trong state machine | **Đồng ý** | Bỏ `RATED`; trạng thái đánh giá = tồn tại doc `ratings/{orderId}` |
| R7 | Mock refund: bỏ "cộng lại ví", chỉ đổi `paymentStatus` | W3 nói không xây ví, UC11 lại cộng ví mock | **Đồng ý** (lỗi thật) | Bỏ thao tác cộng số dư; ghi rõ là hoàn tiền giả lập |
| R8 | Routes API thay Distance Matrix API | D5 dùng Distance Matrix | **Đồng ý** (đã kiểm chứng) | Đổi sang `computeRouteMatrix` + Maps SDK |
| R9 | Backend phải tính lại giá từ menu, không tin client | §6.1 + NFR-03 **đã** yêu cầu; câu §7.1 viết mơ hồ | **Đồng ý một phần** | Sửa câu chữ §7.1; nguyên tắc giữ nguyên |
| R10 | Android không giữ API key | NFR-04 + D3 **đã** yêu cầu; chỉ component diagram vẽ sai | **Đồng ý một phần** | Chỉ sửa sơ đồ 5.3 |
| R11 | User–Store: ERD vẽ 1:N nhưng scope nói 1:1 | Mâu thuẫn tài liệu | **Đồng ý một phần** | Giữ mô hình N (đa chi nhánh), ghi rõ demo seed 1 quán |
| R12 | Driver info: map nhúng hay collection riêng | ERD có **cả hai** | **Đồng ý** (lỗi thật) | Bỏ entity `DRIVER_INFO`; giữ map nhúng trong order |
| R13 | ERD cần bản đồ collection/subcollection thực tế | ERD là mô hình quan hệ khái niệm | **Đồng ý** | Thêm bảng ánh xạ entity → collection/path |
| R14 | Mọi chuyển trạng thái nhạy cảm qua Cloud Functions | Functions cho tiền/shipping; quán ghi status trực tiếp | **Phản biện** | Giữ Functions cho tiền/shipping; accept/reject/ready để rules validate |

---

## 3. Phản biện chi tiết

### 3.1 (R1) Google Sign-In bắt buộc làm tăng rủi ro demo, không giảm

Đúng: `GoogleSignInClient` cũ đã deprecated, nếu dùng Google thì phải dùng Credential Manager. Nhưng thay **hoàn toàn** bằng Google Sign-In kéo theo:

- Phải tạo OAuth client trên Google Cloud, khai báo **SHA-1 của từng signing key** (debug và release là hai key khác nhau).
- Thiết bị quán phải có Google Play Services và **đang đăng nhập một tài khoản Google**. Emulator dùng image AOSP (không có GMS) sẽ **không chạy được**.
- Demo có hai vai trò trên hai máy → cần hai tài khoản Google khác nhau, đăng xuất/đăng nhập làm mất thời gian trong video 2 phút.

Trong khi đó email/password: không phụ thuộc GMS, hai tài khoản seed sẵn, đăng nhập dưới 5 giây. Đề bài **không chấm cơ chế đăng nhập**.

**Kết luận:** thêm Google Sign-In là điểm cộng (trình bày được Credential Manager trong báo cáo), nhưng phải giữ email/password làm đường demo chính. Review đặt "thay thế" là sai hướng; nên là "bổ sung".

### 3.2 (R3) Custom Claims là over-engineering cho đồ án này

Custom Claims có ba cái giá mà review không nhắc:

1. Phải chạy **Admin SDK** (script Node/Python hoặc Function) để gán claim — thêm một môi trường cài đặt.
2. Claim **chỉ cập nhật khi token được refresh**; đổi quyền xong app vẫn thấy quyền cũ tới 1 giờ nếu không gọi `getIdToken(true)`. Đây là nguồn lỗi demo kinh điển ("cấp quyền rồi mà không vào được").
3. Claim không giải quyết quyền **theo từng quán** (`storeId`) — vẫn phải tra Firestore trong rules.

**Đề xuất thay thế tương đương bảo mật, đơn giản hơn:**

```text
users/{uid}.roles        ← chỉ Cloud Function/console ghi; rules: allow write: if false
stores/{storeId}.ownerId ← rules: cho phép cập nhật order khi request.auth.uid == ownerId
```

Rules kiểm tra trực tiếp `ownerId`, không có độ trễ token. Custom Claims để ở mục "hướng production", không đưa vào code.

### 3.3 (R14) "Mọi transition qua Cloud Functions" đụng NFR-01 và chi phí

Review đề nghị đưa cả "xác nhận/từ chối đơn" qua Cloud Functions. Vấn đề:

- **Cold start 1–3 giây** cho Function ít gọi — chính NFR-01 đặt mục tiêu realtime ≤ 3s. Rules-based write là tức thời.
- Mỗi lời gọi Function tính tiền theo invocation + thời gian; demo đồ án không cần.
- Firestore Rules **đủ sức** validate transition hợp lệ: kiểm tra trạng thái cũ, trạng thái mới thuộc tập cho phép, các trường tiền bất biến, người gọi là chủ quán của đơn.

**Đường biên đúng nên chốt:**

| Thao tác | Nơi xử lý | Lý do |
|---|---|---|
| Tạo đơn, tính tiền, trừ lượt voucher | Cloud Function | Tiền — không tin client |
| Báo giá ship | Cloud Function | Giữ API key, gọi đối tác |
| Gọi shipper, nhận webhook tài xế | Cloud Function | Key + xác thực nguồn |
| Hoàn tiền mock | Cloud Function | Tiền |
| Xác nhận / từ chối / READY | Firestore Rules | Nhanh, đủ chặt, giảm chi phí |
| Đánh giá, đặt lại đơn | Firestore Rules | Dữ liệu không nhạy cảm về tiền |

### 3.4 (R4 kèm) Mock provider lên server làm mock lifecycle khó hơn — và rủi ro Blaze bị bỏ sót

Chuyển `MockShippingProvider` sang server có nghĩa vòng đời tài xế giả lập phải do **scheduled function** (quét định kỳ các đơn `SEARCHING_DRIVER`/`DELIVERING` của mock) hoặc Cloud Tasks đẩy. Được, nhưng thêm 2 thành phần.

Quan trọng hơn: **Cloud Functions không deploy được trên gói Spark miễn phí — cần Blaze (gắn thẻ thanh toán)**. Đây là rủi ro hành chính lớn nhất của cả hai bản thiết kế, và review — vốn đẩy *nhiều hơn* logic lên Functions — không nhắc một chữ.

**Giảm thiểu đề xuất:**

- Dùng 1 project Blaze duy nhất cho nhóm, bật **budget alert**, giới hạn `maxInstances=1` cho mọi function.
- Chỉ 4–5 function như bảng 3.3, không function nào chạy định kỳ dày.
- Mock lifecycle: scheduled function chạy mỗi phút (rẻ, dự đoán được) — hoặc nếu muốn zero-cost, để `MockProvider` chạy trong app quán ở chế độ demo và ghi transition trực tiếp (chấp nhận lệch kiến trúc, ghi rõ trong báo cáo).

### 3.5 Hai "phát hiện" đã có sẵn trong bản gốc

- **R9 (không tin giá client):** §6.1 ghi rõ "Tính lại toàn bộ tiền phía server"; NFR-03 ghi "giá tính phía Functions không tin cậy client". Review đúng về mặt nguyên tắc nhưng không phải điểm thiếu; chỉ câu §7.1 ("snapshot ... để món đổi giá không làm sai đơn") viết gây hiểu nhầm → sửa câu.
- **R10 (Android không giữ API key):** NFR-04 và cột lý do của D3 đã yêu cầu đúng. Lỗi thật duy nhất là **sơ đồ 5.3** vẽ `ahamove-provider` trong tầng `data` của app → sửa sơ đồ.

---

## 4. Các điểm review bỏ sót (bổ sung)

1. **Quy tắc đồng bộ 3 trạng thái** — review tách trường nhưng không nói ai giữ chúng nhất quán. Cần định nghĩa: `shippingStatus = DELIVERED` ⇒ Function đặt `orderStatus = COMPLETED`; COD thì `paymentStatus: COD_PENDING → PAID` tại chính thời điểm đó. Không có quy tắc này, 3 trường sẽ lệch nhau.
2. **`shippingStatus: FAILED` không có đường về** — cần quy tắc: `FAILED` ⇒ quán được gọi lại (về `SEARCHING_DRIVER`) hoặc từ chối đơn.
3. **Ai được ghi `paymentStatus`** — review liệt kê trạng thái nhưng không nói chủ thể; phải là Function/webhook, rules chặn client.
4. **Đánh giá sau khi bỏ `RATED`** — cần chỗ hiển thị "chưa đánh giá": query `ratings` theo `orderId` ở màn chi tiết đơn.
5. **FCM khi app bị kill** — bản gốc đã có trong bảng rủi ro; review không nhắc nên dễ tưởng đã giải quyết.
6. **Chi phí Routes API** — Routes API tính theo SKU khác; với demo vài chục lần gọi mỗi ngày thì không đáng kể, nhưng nên cache báo giá trong phiên checkout để tránh gọi lặp.

---

## 5. Chốt danh sách sửa bản chính

**Bắt buộc (làm ngay, sửa lỗi thật):**

1. Tách `orderStatus` / `shippingStatus` / `paymentStatus` + quy tắc dẫn xuất (R5, R6, bổ sung 4.1–4.2).
2. Bỏ "cộng lại ví mock" trong UC11; refund chỉ đổi trạng thái (R7).
3. Đổi Distance Matrix → Routes API `computeRouteMatrix` (R8).
4. Chốt `ShippingProvider` + implement chỉ tồn tại server-side; sửa sơ đồ 5.3 (R4, R10).
5. Bỏ entity `DRIVER_INFO`, giữ map nhúng `orders.driver` (R12).
6. Vai trò không do client tự chọn; ghi bởi backend, kiểm bằng rules (R2).
7. Thêm bảng ánh xạ entity → collection/path Firestore (R13).
8. Sửa câu chữ §7.1 về snapshot giá; ghi rõ backend tính lại từ menu (R9).
9. Làm rõ mô hình User–Store: cho phép N trong dữ liệu, demo seed 1 (R11).

**Nên làm (điểm cộng, không tốn nhiều công):**

10. Thêm Google Sign-In qua Credential Manager như **tùy chọn song song**, không thay email/password (R1).
11. Bảng "đường biên Cloud Functions vs Rules" như 3.3 — đưa vào tài liệu để bảo vệ.
12. Mục rủi ro mới: **yêu cầu Blaze + budget alert** (3.4).

**Không làm (để mục "hướng production" trong báo cáo):**

13. Firebase Custom Claims (R3).
14. Đưa toàn bộ transition lên Cloud Functions (R14).

---

## 6. Tài liệu tham khảo đã kiểm chứng

- Google Maps Platform, Distance Matrix API — trạng thái Legacy, từ 10/2026 không thể bật cho project Google Cloud mới: https://developers.google.com/maps/documentation/distance-matrix
- Google Maps Platform, Migrate to Routes API — endpoint `distanceMatrix/v2:computeRouteMatrix`: https://developers.google.com/maps/documentation/routes/migrate-routes
- Firebase Authentication — Google Sign-In trên Android: https://firebase.google.com/docs/auth/android/google-signin
- Android Developers — Credential Manager: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- Firebase — Custom Claims: https://firebase.google.com/docs/auth/admin/custom-claims
