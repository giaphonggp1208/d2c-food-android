# Đề tài cuối kỳ môn Phát triển ứng dụng di động - HK1/2026-2027

## NỀN TẢNG ĐẶT MÓN & ĐIỀU PHỐI GIAO HÀNG TỰ ĐỘNG (D2C F&B PLATFORM)

**Mục tiêu:** Hệ thống cho phép các quán ăn (ví dụ: bún riêu, bún chả cá) nhận đơn trực tiếp từ khách hàng và tự động kết nối với các đơn vị vận chuyển (AhaMove, Lalamove). Quá trình vận hành diễn ra hoàn toàn tự động, loại bỏ việc chủ quán phải nhập đơn thủ công sang các ứng dụng giao hàng khác.

---

### 1. Quản lý Thực đơn & Khuyến mãi

Đây là nghiệp vụ thiết lập ban đầu do Chủ quán thực hiện.

* **Cấu trúc Thực đơn:** Cho phép món ăn được chia thành các nhóm rõ ràng (Ví dụ: Món chính, Món thêm, Thức uống, ...).
* **Nghiệp vụ Tùy chỉnh:** Hệ thống phải cho phép khách hàng linh hoạt tùy biến món ăn.
  * *Chọn một (Bắt buộc):* Kích cỡ phần ăn (Ví dụ: Tô thường, Tô lớn, Trẻ em).
  * *Chọn nhiều (Không bắt buộc):* Thêm món ăn kèm (Thêm chả, thêm giò, mắm tôm riêng).
* **Quản lý Khuyến mãi:** Chủ quán có thể tạo các mã giảm giá (Voucher) theo tỷ lệ phần trăm (VD: Giảm 10%) hoặc số tiền cố định (VD: Giảm 20.000đ). Hệ thống cần kiểm tra điều kiện áp dụng (VD: Đơn tối thiểu 100.000đ, hạn sử dụng, số lượng mã tối đa, ...).

### 2. Trải nghiệm Khách hàng & Đặt hàng

* **Giỏ hàng:** Khách hàng có thể đặt cùng một món nhưng với các yêu cầu khác nhau, hệ thống phải tách biệt chúng. (VD: Đặt 2 tô bún, 1 tô "không hành" và 1 tô "nhiều hành", giỏ hàng phải hiển thị thành 2 dòng riêng biệt, không được gộp chung thành số lượng 2).
* **Báo giá Vận chuyển Tự động:** Khi khách nhập địa chỉ nhận hàng, hệ thống sẽ tự động liên kết với đối tác vận chuyển (AhaMove/Lalamove) để lấy báo giá cước phí chính xác theo khoảng cách thực tế (kilomet) ngay tại thời điểm đặt. Khách hàng nhìn thấy rõ phí ship trước khi quyết định thanh toán.
* **Tính toán Tổng tiền:** Công thức tính hóa đơn minh bạch: `(Tiền món ăn + Tiền món thêm) - Tiền giảm giá khuyến mãi + Phí giao hàng`. (Lưu ý: Mã giảm giá không được áp dụng để trừ vào phí giao hàng).

### 3. Xử lý Đơn hàng tại Quán

Tối ưu hóa quy trình làm việc của bếp, tránh sai sót hoặc chậm trễ.

* **Tiếp nhận Đơn hàng:** Khi có đơn mới, hệ thống báo chuông tại thiết bị của quán. Chủ quán kiểm tra đơn và bấm "Xác nhận nhận đơn", lúc này khách hàng sẽ nhận được thông báo "Quán đang chuẩn bị món".
* **Hiển thị tại Bếp:** Giao diện xem đơn hàng phải làm nổi bật các yêu cầu đặc biệt của khách (VD: Không cay, bị dị ứng) để đầu bếp chú ý trước khi đóng gói.
* **Kiểm soát Tồn kho đột xuất:** Nếu quán đột ngột hết một nguyên liệu (VD: Hết chả cá), chủ quán có quyền bấm "Từ chối đơn" và hệ thống lập tức thông báo lý do, kèm thao tác hoàn tiền (nếu khách đã thanh toán trước) cho khách hàng.
* **Thay đổi trạng thái hoạt động:** Quán có thể chuyển sang các trạng thái: Đang mở cửa, quán bận (15 phút, 30 phút, 1 tiếng, 1 ngày, ...), đóng cửa, và cài đặt lịch hoạt động thường ngày cũng như lịch mở/đóng cửa tùy chỉnh.

### 4. Điều phối Vận chuyển Tự động

* **Gọi Shipper:** Sau khi bếp đóng gói xong món ăn, chủ quán bấm nút "Gọi Shipper". Hệ thống sẽ tự động gửi toàn bộ thông tin (Địa chỉ quán, địa chỉ khách, số tiền cần thu hộ, ghi chú giao hàng) sang hệ thống đối tác vận chuyển để tìm tài xế.
* **Đồng bộ Thông tin:** Ngay khi hệ thống đối tác tìm được tài xế, các thông tin bao gồm Tên tài xế, Số điện thoại và Biển số xe sẽ được truyền về. Khách hàng lập tức xem được thông tin này trên ứng dụng của mình để chủ động liên lạc.

### 5. Theo dõi Lộ trình & Chăm sóc Sau mua

* **Cập nhật Tiến độ Giao hàng:** Mỗi khi tài xế thay đổi trạng thái (Đã đến quán $\rightarrow$ Đã lấy món $\rightarrow$ Đang giao $\rightarrow$ Hoàn thành), ứng dụng sẽ tự động gửi thông báo báo rung (Push Notification) đến điện thoại khách hàng. Chủ quán cũng xem được trạng thái này để kiểm soát rủi ro (VD: Đơn bị giao quá lâu).
* **Đánh giá & Phản hồi:** Ngay sau khi trạng thái đơn chuyển sang "Giao thành công", khách hàng có thể chấm điểm (1-5 sao) và để lại nhận xét.
* **Tái mua hàng:** Khách hàng có thể vào Lịch sử đơn hàng, chọn một đơn cũ đã giao thành công và bấm "Đặt lại đơn này" để tiết kiệm thời gian chọn lại món và cấu hình topping.

---

## Cần nộp lại

1. **Source code của ứng dụng**
2. **File báo cáo** (word/pdf)
3. **File trình chiếu**
4. **Bảng phân chia công việc các thành viên trong nhóm**
5. **Videos demo các chức năng của ứng dụng**
   * Mỗi chức năng 1 video
   * Mỗi video không quá 2 phút.