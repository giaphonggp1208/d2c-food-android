# VIETMAP Implementation Plan — D2C F&B

## 0. Mục tiêu và ranh giới

Tích hợp **VIETMAP Android Maps SDK** vào ứng dụng Kotlin + Jetpack Compose đang dùng Firebase Spark và Firestore.

### Trong scope

- Hiển thị bản đồ VIETMAP trong Compose.
- Dùng Tilemap key với quota giới hạn.
- Hiển thị marker quán, khách và tài xế mô phỏng.
- Tính khoảng cách đường thẳng bằng Haversine hoặc cho nhập km thủ công.
- Tính phí ship ước tính theo đơn giá/km của Store.
- Kết nối dữ liệu tracking với `MockShippingProvider` và `shippingStatus`.

### Ngoài scope

- Không gọi VIETMAP Route/Matrix/Autocomplete/Place API trực tiếp từ Android.
- Không nhúng Services/private key vào APK.
- Không dùng Matrix API cho một quán và một khách.
- Không gọi AhaMove/Lalamove thật.
- Không triển khai backend proxy trong phase này.
- Không dùng Google Maps Platform hoặc Firebase Cloud Functions.

## 1. Trạng thái trước khi bắt đầu

Phase 1 đã hoàn tất:

- Kotlin + Jetpack Compose build xanh.
- Firebase Auth + Firestore Repository.
- `google-services.json` đã cấu hình.
- Role Customer/Store Owner.
- Firestore Security Rules.
- 29/29 Rules tests pass.
- Gradle wrapper và README đã có.

Code hiện chưa có:

- Cart/checkout.
- `MockShippingProvider`.
- `MapRepository`.
- VIETMAP SDK.
- Order tracking screen.
- Local notification.

## 2. Quy tắc API key

VIETMAP có hai nhóm key cần phân biệt:

### 2.1 Tilemap key

Dùng cho bản đồ nền của Mobile Maps SDK.

- Có thể xuất hiện trong cấu hình client theo hướng dẫn SDK.
- Chỉ bật Tilemap API.
- Đặt quota thấp phù hợp demo.
- Theo dõi Daily Report.
- Không dùng key này cho Route, Matrix, Place hoặc Autocomplete.

### 2.2 Services key

Dùng cho Route, Matrix, Autocomplete và Place API.

Không được đưa vào:

- Kotlin source.
- `BuildConfig`.
- `res/values`.
- `AndroidManifest.xml`.
- `google-services.json`.
- GitHub.

Nếu cần Services API sau này:

```text
Android → HTTPS backend proxy → VIETMAP Services API
```

Services key phải nằm trong environment variable hoặc secret manager ở backend.

## 3. Phase VM-0 — Chuẩn bị VIETMAP Console

### Việc thực hiện

1. Đăng ký/đăng nhập VIETMAP Console.
2. Tạo project hoặc consumer cho ứng dụng.
3. Tạo Tilemap key.
4. Chỉ bật dịch vụ map tile cần thiết.
5. Đặt quota thấp cho môi trường demo.
6. Ghi lại tên key trong file local không commit.
7. Kiểm tra Daily Report và số lượt còn lại.

### Tiêu chí hoàn thành

- Có Tilemap key hoạt động.
- Biết rõ key thuộc project nào.
- Không có Services key trong repository.
- Có phương án rotate key nếu key client bị lộ.

## 4. Phase VM-1 — SDK spike tối thiểu

Không triển khai checkout ở phase này. Chỉ xác nhận SDK.

### Việc thực hiện

1. Thêm repository/dependency theo tài liệu VIETMAP.
2. Tạo màn hình `VietmapSpikeScreen` tạm thời.
3. Tạo `MapView` theo SDK.
4. Nhúng `MapView` vào Compose bằng `AndroidView`.
5. Khởi tạo SDK và load style URL với Tilemap key.
6. Xử lý lifecycle Activity/MapView.
7. Chạy trên emulator API 37.
8. Tắt/mở Activity để kiểm tra không crash.

Tài liệu hiện hướng dẫn XML `MapView`, chưa xác nhận Compose wrapper chính thức. Vì vậy `AndroidView` là adapter cần kiểm chứng, không coi là API chính thức của VIETMAP.

### Tiêu chí hoàn thành

- Bản đồ render được trên emulator.
- Không crash khi xoay/khởi động lại màn hình trong phạm vi demo.
- Camera mặc định đến khu vực Việt Nam/Đà Nẵng.
- Tilemap key không bị hardcode vào repository.
- Dependency tương thích với compile SDK, AGP và Kotlin hiện tại.

### Fallback

Nếu SDK không resolve hoặc không chạy ổn định:

- Giữ `MapRepository` interface.
- Dùng `MapPreview` giả lập với Canvas/ảnh tĩnh cho demo.
- Không làm chậm Cart, Order và MockShippingProvider.

## 5. Phase VM-2 — Domain abstraction

Không để ViewModel hoặc UseCase phụ thuộc trực tiếp VIETMAP classes.

### Domain models

```text
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class DistanceEstimate(
    val distanceKm: Double,
    val method: DistanceMethod
)

data class ShippingQuote(
    val distanceKm: Double,
    val shippingFee: Long,
    val isEstimated: Boolean
)

data class MapMarker(
    val id: String,
    val point: GeoPoint,
    val type: MarkerType
)
```

### Interfaces

```text
interface MapRepository {
    fun calculateDistance(origin: GeoPoint, destination: GeoPoint): DistanceEstimate
    fun calculateShippingQuote(
        origin: GeoPoint,
        destination: GeoPoint,
        pricePerKm: Long
    ): ShippingQuote
}
```

`MapRepository` không trả về VIETMAP `MapView`, `LatLng` hoặc SDK-specific object.

### Tiêu chí hoàn thành

- Haversine có unit test.
- Shipping fee có unit test.
- Discount không trừ vào shipping fee.
- Không cần Android emulator để test Domain.

## 6. Phase VM-3 — Store và delivery location

### Store data

Bổ sung `location` vào Store document:

```json
{
  "ownerId": "OWNER_UID",
  "name": "Bún Cá Demo",
  "location": {
    "latitude": 16.0471,
    "longitude": 108.2068
  },
  "fallbackPerKmFee": 5000
}
```

Mapper `FirestoreStoreRepository` phải đọc `GeoPoint` từ Firestore và map sang Domain `GeoPoint`.

### Delivery location

Phase đầu dùng một trong hai cách:

- Chọn điểm demo trên bản đồ.
- Seed tọa độ khách trong Checkout state.

Không bắt buộc xin GPS thật ở phase đầu. GPS thật là phase mở rộng vì cần runtime permission và xử lý emulator.

## 7. Phase VM-4 — Haversine và shipping quote

### Công thức

```text
distanceKm = haversine(storeLocation, deliveryLocation)
shippingFee = ceil(distanceKm × fallbackPerKmFee)
```

### UI bắt buộc ghi rõ

```text
Khoảng cách ước tính: 3.4 km
Phí giao hàng ước tính: 17.000đ
Khoảng cách đường thẳng dùng cho bản demo.
```

Không gọi Haversine là khoảng cách đường bộ thực tế.

### Fallback

Nếu thiếu tọa độ:

- Cho nhập km thủ công.
- Đánh dấu `isEstimated = true`.
- Không gọi VIETMAP Route API.

## 8. Phase VM-5 — MockShippingProvider

Giữ interface trong Domain:

```text
interface ShippingProvider {
    suspend fun createDelivery(orderId: String): Result<DeliveryInfo>
    suspend fun advanceStatus(orderId: String, next: ShippingStatus): Result<Unit>
}
```

`MockShippingProvider` tạo dữ liệu demo:

```text
name: Nguyễn Văn A
phone: 0900000000
vehiclePlate: 43A-123.45
```

Không dùng dữ liệu cá nhân thật.

Các transition:

```text
NOT_REQUESTED
→ SEARCHING_DRIVER
→ DRIVER_ASSIGNED
→ ARRIVED_AT_STORE
→ PICKED_UP
→ DELIVERING
→ DELIVERED
```

`MockShippingProvider` không import VIETMAP SDK. Nó chỉ cập nhật domain state qua `OrderRepository` và Security Rules.

## 9. Phase VM-6 — Tracking map

### Marker

- `STORE`: vị trí quán.
- `CUSTOMER`: vị trí giao hàng.
- `DRIVER`: vị trí mô phỏng.

### Mapping trạng thái

| shippingStatus | UI map |
|---|---|
| `NOT_REQUESTED` | Marker quán và khách |
| `SEARCHING_DRIVER` | Loading tìm tài xế |
| `DRIVER_ASSIGNED` | Marker tài xế xuất hiện |
| `ARRIVED_AT_STORE` | Tài xế tại quán |
| `PICKED_UP` | Tài xế rời quán |
| `DELIVERING` | Tài xế di chuyển về khách |
| `DELIVERED` | Tài xế tại điểm khách |

### Vị trí mô phỏng

Dùng các điểm trung gian:

```text
driverAtStore
 driverMidRoute
driverAtCustomer
```

Không cần polyline Route API trong bản đầu.

## 10. Phase VM-7 — Integration với Checkout

Checkout chỉ phụ thuộc abstraction:

```text
CheckoutViewModel
    → MapRepository.calculateShippingQuote()
    → ShippingQuote
    → PricingCalculator
    → PlaceOrder transaction
```

Checkout không phụ thuộc `VietmapMapView`.

### Order snapshot

Lưu vào Order:

```text
distanceKm
shippingFee
shippingFeeIsEstimated
storeLocation
 deliveryLocation
```

Mục tiêu là lịch sử đơn không thay đổi khi đơn giá/km của quán thay đổi.

## 11. Phase VM-8 — Testing

### Unit tests

- Haversine với hai tọa độ đã biết.
- Tọa độ giống nhau cho khoảng cách 0.
- Phí ship làm tròn lên.
- Không âm khi dữ liệu lỗi.
- Discount không ảnh hưởng shipping fee.

### UI/manual tests

- Map render trên emulator API 37.
- Map vẫn hiển thị sau recreate Activity.
- Marker quán và khách đúng vị trí.
- Marker tài xế thay đổi theo state.
- Không crash khi không có delivery location.
- Không crash khi Tilemap key hết quota.

### Security tests

- Tilemap key không được dùng cho Services API.
- Services key không xuất hiện trong APK.
- Security Rules order transition vẫn pass 29 test hiện có.
- Store location không cho Customer ghi.

## 12. Phase VM-9 — Video demo

### Video Map/Checkout

1. Khách mở Checkout.
2. Bản đồ VIETMAP hiển thị.
3. Marker quán hiển thị.
4. Chọn hoặc nhập delivery location.
5. Hiển thị khoảng cách Haversine ước tính.
6. Hiển thị phí ship.
7. Hiển thị tổng tiền.

### Video Tracking

1. Quán bấm Gọi Shipper.
2. Mock gán tài xế.
3. Khách thấy tên, số điện thoại, biển số.
4. Quán chuyển từng trạng thái.
5. Marker tài xế cập nhật.
6. Delivered → Completed → mở đánh giá.

## 13. Phân công đề xuất

| Thành viên | Phạm vi | Phụ thuộc |
|---|---|---|
| TV1 | Menu detail + option groups | Domain MenuItem/OptionGroup |
| TV2 | Cart + PricingCalculator + voucher transaction | Cart/Order models |
| TV3 | Store dashboard + kitchen + OrderRepository + Rules | Order state machine |
| TV4 | VIETMAP spike + MapRepository + Map adapter | GeoPoint/ShippingQuote |
| Chung | Checkout integration + MockShippingProvider + tracking + notification | Các interface đã chốt |

### Quy tắc tích hợp

- TV1–TV3 không import VIETMAP SDK.
- TV4 không đổi Order state machine.
- Mọi người dùng chung `GeoPoint`, `ShippingQuote`, `MapRepository`, `ShippingProvider` và `OrderRepository`.
- Không commit API Services key.
- Mỗi module có unit test hoặc Rules test tương ứng.

## 14. Tài liệu VIETMAP tham khảo

- API key: https://maps.vietmap.vn/docs/map-api/console/register-api-key/
- Quickstart: https://maps.vietmap.vn/docs/map-api/quickstart/
- Key management: https://maps.vietmap.vn/docs/vi/map-api/console/manage-api-key/
- Quota: https://maps.vietmap.vn/docs/vi/map-api/console/usage-limitation/
- Android Maps SDK: https://maps.vietmap.vn/docs/sdk-mobile/sdk-android/map/
- Route API v4: https://maps.vietmap.vn/docs/map-api/route-version/route-v4/
- Matrix API v4: https://maps.vietmap.vn/docs/map-api/matrix-version/matrix-v4/
- Autocomplete API v4: https://maps.vietmap.vn/docs/map-api/autocomplete-version/autocomplete-v4/
- AndroidView in Compose: https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose

## 15. Definition of Done

VIETMAP phase chỉ được coi là hoàn thành khi:

- Map render trên emulator API 37.
- Tilemap key có quota giới hạn và không dùng Services key.
- `MapRepository` có unit tests.
- Store/Customer markers hiển thị đúng.
- Driver mock marker cập nhật theo shipping status.
- Checkout nhận `ShippingQuote` qua interface.
- Không có Route/Matrix/Autocomplete/Place key trong APK.
- Không làm hỏng 29 Firestore Rules tests.
- Video demo map và tracking quay được.
