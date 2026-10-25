# Generator spec — dataset tổng hợp cho Challenge 10

> Bản đặc tả để duyệt **trước khi code** Task 12–15. Mỗi quy tắc ghi nguồn:
> - `Dx`, `Rx`, `Qx`: mục trong `design-decisions.md` (phụ lục Q1–Q24 được ưu tiên khi khác thân).
> - `P:Tn`: ràng buộc đã ghi trong `docs/PROGRESS.md` cho task n.
> - `Đề`: `docs/AI_Challenge_10.md`.
> - **Mới**: đề xuất của bản đặc tả này, chưa có nguồn. Cần duyệt; các mục này được liệt kê lại ở mục 9.
>
> Mọi con số "ước lượng" là tính tay từ tham số, **chưa phải số chạy ra**.

## 1. Tổng quan

| Tham số | Giá trị | Nguồn |
|---|---|---|
| Tổng số claim | đúng 2.000 | Đề, D6 |
| Claim gian lận | đúng 200: duplicate 30, rapid 30, upcoding 30, unbundling 25, phantom 25, weekend 20, mismatch 20, clustering 20 | Đề, D10-B |
| Claim hợp lệ | 1.800, gồm: `none`, 20 `legit_noise` clustering, 6 `legit_noise` ngày phantom, khoảng 25 `legit_near_miss`, 30 claim của kịch bản ngày 30 claim | Q3, Q7, Q8 |
| Member | đúng 500 (`M-0001`…`M-0500`), **mỗi member có ít nhất 1 claim** | D6; **Mới** (bảo đảm "500 unique") |
| Provider | đúng 50 (`PRV-01`…`PRV-50`), mỗi provider có ít nhất 1 claim | D6 |
| Ngày | 2024-01-01 đến 2024-12-31 (366 ngày) | Đề |
| Định dạng | `claims.csv` (10 cột) và `ground_truth.csv` riêng | D2, D3 |
| `claim_id` | `CLM-00001`…`CLM-02000`, gán **sau cùng** theo thứ tự ngày (D14); cách phá hoà trong cùng ngày xem mục 4.7 | D14 |

**Dataset và seed** (Q6, Q16):

| Bộ | Seed | Thư mục | Dùng để |
|---|---|---|---|
| Tuning | 42 | `data/tuning/` | Chọn T (Task 17), quyết định R4 (Q2) |
| Held-out | 2024 | `data/heldout/` | Số liệu chính trong README; chỉ chạy sau khi đã commit `config/frozen-config.json` (Q10) |
| Ổn định | 101–105 | `data/seeds/<seed>/` | Mean ± SD của metric với config đã đóng băng (M3, H3) |

**Tính tất định** (D1):
- Lệnh: `generate --seed <n> --out <dir> [--generator-config <path>]`.
- Cùng seed và cùng `generator-config.json` phải cho ra file giống hệt từng byte (có test).
- Bất biến cần giữ: không lặp trên `HashMap`/`HashSet` khi thứ tự lặp ảnh hưởng tới kết quả; danh mục đọc từ `Catalog` (đã tất định); mọi lựa chọn ngẫu nhiên đi qua RNG theo một thứ tự gọi cố định.
- **RNG (G1, đã chốt):** mỗi giai đoạn có một `SplittableRandom` riêng. Seed của nó là `mix64(seed XOR fnv1a64(tên giai đoạn))`, trong đó `mix64` là hàm trộn SplitMix64. Không dùng công thức tuyến tính kiểu `seed × 31 + mã`. Seed giai đoạn chỉ phụ thuộc vào seed gốc và tên giai đoạn, nên thêm hoặc bớt lượt rút ở một giai đoạn không làm đổi các giai đoạn khác.
- **Rút lại lựa chọn (G2, đã chốt):** khi một lựa chọn (member, ngày, provider…) vi phạm ràng buộc **của generator**, generator rút lại ngay trong lúc sinh, tối đa 1.000 lần; quá giới hạn thì ném lỗi. Việc rút lại **không bao giờ** dựa trên kết quả rule của engine. Điểm này sẽ ghi rõ trong writeup.

**Thứ tự các giai đoạn** (cố định):
1. Provider.
2. Member.
3. Claim hợp lệ nền.
4. Kịch bản cố định: nhóm clustering, ngày phantom, ngày 30 claim.
5. Tám pattern gian lận, theo thứ tự đề: duplicate, rapid, upcoding, unbundling, phantom, weekend, mismatch, clustering.
6. `legit_noise` và `legit_near_miss`.
7. Gán `claim_id`.
8. Ghi file.

## 2. Provider

| Tầng | Số lượng | claim_type phục vụ | Claim hợp lệ nền | Nguồn |
|---|---:|---|---|---|
| Bệnh viện "chỉ làm ngày thường" | 8 | INPATIENT, OUTPATIENT | **đúng 65 claim mỗi nơi** (quota), chỉ ngày thường | Q13 (≥ 60); quota 65 là **Mới** |
| Bệnh viện thường | 4 | INPATIENT, OUTPATIENT | phần INPATIENT/OUTPATIENT bệnh viện còn lại, khoảng 45–50 mỗi nơi | D6-B; số lượng là **Mới** |
| Phòng khám | 28 | OUTPATIENT | khoảng 70% claim OUTPATIENT, chia lệch (trọng số Gamma) | D6-B; **Mới** |
| Nha khoa | 10 | DENTAL | toàn bộ DENTAL, chia lệch | D6-B, D7 |

**Quy tắc:**
- Bệnh viện nhận **mọi** claim INPATIENT và khoảng 30% claim OUTPATIENT. Phòng khám nhận khoảng 70% OUTPATIENT. *(D7; tỉ lệ là **Mới**)*
- **Bệnh viện chỉ làm ngày thường:** mọi claim hợp lệ đều vào thứ Hai–thứ Sáu, nên tỉ lệ cuối tuần hợp lệ là 0. *(Q13)*
  - Ngoài 2–3 claim weekend anomaly, **mọi claim gian lận khác** đặt tại các bệnh viện này cũng phải vào ngày thường. *(**Mới**, suy ra từ D11)*
  - Lý do: chỉ cần thêm vài claim cuối tuần, kể cả không phẫu thuật, là tỉ lệ leave-one-out của một claim weekend gian lận có thể vượt 5%. Ví dụ provider có 65 claim nền và 3 claim weekend gian lận. Thêm 1 claim rapid rơi vào Chủ Nhật: tổng 69, mỗi claim weekend gian lận thấy 3/68 = 4,4%, vẫn bị bắt. Thêm 2 claim như vậy: tổng 70, thấy 4/69 = 5,8%, và claim weekend gian lận bị bỏ sót.
- **Provider thường:** ngày rải đều trên 366 ngày, nên tỉ lệ cuối tuần tự nhiên khoảng 2/7 = 28,6%. *(D6)*
- **Gán `provider_id`:** tạo tầng trước, rồi **xáo** việc gán `PRV-01`…`PRV-50` bằng RNG, để mã provider không lộ tầng. *(**Mới**)*
- **`provider_name`:** tên hư cấu ghép từ danh sách từ, ví dụ "Riverside General Hospital", "Lotus Family Clinic", "Bright Smile Dental". Không dùng tên cơ sở y tế có thật. *(**Mới**)*
- **Provider ngày phantom:** một phòng khám tầm trung, không thuộc nhóm chỉ làm ngày thường. *(Q8)*
- **Provider ngày 30 claim:** một phòng khám khác. *(Q7, **Mới**)*

## 3. Member

| Tham số | Giá trị | Nguồn |
|---|---|---|
| Phân phối số claim mỗi member | Lệch: trọng số member ~ Gamma(shape 1,5), trung bình khoảng 3,6 claim mỗi người, đuôi phải tới khoảng 12–15 | D6-B; tham số là **Mới** |
| Bảo đảm 500 member | 500 claim nền đầu tiên gán mỗi member đúng một lần, các claim còn lại theo trọng số | **Mới** |
| Member mạn tính | 20% member có một chẩn đoán mạn tính (I10, E11.9 hoặc M17.1); khoảng 60% claim OUTPATIENT của họ dùng chẩn đoán đó | **Mới** |
| Tái khám tự nhiên trong 7 ngày | Xem **câu hỏi G3** | D13-C (đang là "hướng mở rộng") |

**Rapid hợp lệ tự nhiên:** member có nhiều claim cùng chẩn đoán (đặc biệt member mạn tính) tự nhiên sẽ có một số cặp cách nhau ≤ 7 ngày. Các cặp này là FP thật của rule rapid và giữ nhãn `none` (D12-A: claim hợp lệ tự nhiên kích hoạt rule thì giữ nguyên, tính là FP). Generator **không** đếm số cặp này; engine đo ở Task 17.

## 4. Claim hợp lệ

### 4.1 Chuỗi sinh (D7)

provider (tầng) → `claim_type` → chẩn đoán (theo bảng 4.3, hoặc chẩn đoán mạn tính của member) → template procedure (bảng 4.3) → giá → ngày → member.

- Mọi template chỉ dùng cặp hợp lệ trong whitelist.
- Mọi procedure của template đều có `claim_type` của claim. *(D7; Task 02 ghi chú về J18.9 + P-1002)*
- Mã chung P-0001/P-0002 **chỉ** xuất hiện như claim một procedure. *(Task 02 (c)4)*
- Claim chỉ có P-0001 với chẩn đoán chỉ nội trú có trọng số rất thấp: 1% claim INPATIENT. *(P:T12)*
- Claim hợp lệ **luôn** dùng mã gói, không bao giờ có đủ bộ thành phần. Có thể có một phần thành phần, ví dụ I10: P-1005 + P-1006. *(Task 02 (c)3)*

### 4.2 Cơ cấu claim_type (claim hợp lệ nền)

| claim_type | Tỉ lệ | Ước lượng (trên khoảng 1.719 claim `none`) | Nguồn |
|---|---:|---:|---|
| OUTPATIENT | 55% | khoảng 945 | **Mới** |
| INPATIENT | 25% | khoảng 430 (gồm 4 nhóm clustering, xem 4.5) | **Mới** |
| DENTAL | 20% | khoảng 344 | **Mới** |

### 4.3 Tần suất chẩn đoán và template (trọng số trong từng claim_type)

**OUTPATIENT**

| Chẩn đoán | Trọng số | Template (trọng số trong chẩn đoán) |
|---|---:|---|
| J06.9 | 18 | P-0001 (40) · P-1001 (25) · P-1001 + P-1002 (35) |
| J02.9 | 12 | P-0001 (40) · P-1008 (60) |
| I10 | 16 | P-0002 (30) · P-1005 (25) · P-1102 (25) · P-1005 + P-1006 (20) |
| E11.9 | 14 | P-0002 (30) · P-1004 (30) · P-1101 (40) |
| M54.5 | 10 | P-1009 (50) · P-1010 (50) |
| N39.0 | 10 | P-1003 (40) · P-1103 (60) |
| H10.9 | 6 | P-1011 (100) |
| J18.9 | 6 | P-1002 (60) · P-1001 + P-1002 (40) |
| M17.1 | 8 | P-1010 (70) · P-0002 (30) |

**DENTAL**

| Chẩn đoán | Trọng số | Template |
|---|---:|---|
| K02.1 | 35 | P-3002 (60) · P-3001 (25) · P-3005 (15) |
| K05.1 | 25 | P-3001 (80) · P-0002 (20) |
| K04.0 | 20 | P-3101 (70) · P-3006 (30) |
| K01.1 | 10 | P-3007 (100) |
| K08.1 | 10 | P-3008 (100) |

**INPATIENT** (không tính 4 nhóm clustering, vì chúng được sinh theo quota ở 4.5)

| Chẩn đoán | Trọng số | Template |
|---|---:|---|
| J18.9 | 22 | P-2001 + P-2002 (70) · P-2001 (30) |
| K35.8 | 18 | P-2101 (100) |
| K80.2 | 14 | P-2102 (100) |
| I21.9 | 6 | P-2010 + P-2011 (100); P-2010 và P-2010 + P-2001 nằm trong quota 4.5 |
| I63.9 | 10 | P-2003 + P-2001 (100) |
| S72.0 | 10 | P-2012 (100) |
| O80 | 12 | P-2008 (100) |
| M17.1 | 7 | P-2013 (100) |
| (mọi chẩn đoán chỉ nội trú) | 1 | P-0001 (chẩn đoán chọn đều trong 7 mã chỉ nội trú) |

Toàn bộ trọng số trong 4.2 và 4.3 là **Mới**. Tổ hợp nhiều procedure lấy theo gợi ý đã duyệt ở Task 02 (d), cộng hai tổ hợp của phương án 3.

### 4.4 Giá (D8, D4)

- `amount = round(Σ base_price × m)`, với `m ~ N(1; 0,15)` cắt cụt tại ±3σ, tức m ∈ [0,55; 1,45]. Lấy mẫu lại khi m nằm ngoài khoảng. Kết quả là `long` ≥ 1.
- Generator **ghi nhớ m** của từng claim. Đây là thông tin nội bộ, không ghi ra file, dùng để chọn bản gốc ở mục 5 (xem G7).

### 4.5 Bốn nhóm chứa dải clustering (Task 02 phương án 3, P:T12)

Bốn nhóm: P-2010 (một procedure), P-2009 (một procedure), I21.9: P-2010 + P-2001, O82: P-2009 + P-2001.

**Tính sức chứa:**
- Có 40 claim cài: 20 clustering gian lận + 20 R9 `legit_noise`. Chia đều 10 claim cài mỗi nhóm.
- Ràng buộc "claim cài ≤ 25% mỗi nhóm" buộc mỗi nhóm có **≥ 40 claim**, tức **≥ 30 claim tự nhiên**. Điều kiện này cũng đáp ứng luôn "≥ 30 claim" của P:T12.
- Nếu để ngẫu nhiên thì số claim mỗi nhóm dao động và có thể rơi dưới 30. Vì vậy dùng **quota cố định: đúng 32 claim tự nhiên mỗi nhóm**. Khi đó mỗi nhóm có 42 claim, claim cài chiếm 10/42 = 23,8%. *(**Mới**)*
- Tổng 4 nhóm là 168 claim, tức 8,4% dataset (G6, đã chấp nhận).
- **6 claim near-miss quanh dải** (mục 6.2: 3 claim 47.000–47.499, 3 claim 50.000–50.500) nằm **trong** quota 32, không cộng thêm. Chia 2 / 2 / 1 / 1 cho P-2010 / P-2009 / I21.9 combo / O82 combo. Vì vậy mỗi nhóm có:

  | Nhóm | Tự nhiên ngẫu nhiên | Near-miss | Cài (gian lận + noise) | Tổng | Tỉ lệ cài |
  |---|---:|---:|---:|---:|---:|
  | P-2010 | 30 | 2 | 5 + 5 | 42 | 23,8% |
  | P-2009 | 30 | 2 | 5 + 5 | 42 | 23,8% |
  | I21.9: P-2010 + P-2001 | 31 | 1 | 5 + 5 | 42 | 23,8% |
  | O82: P-2009 + P-2001 | 31 | 1 | 5 + 5 | 42 | 23,8% |

  Near-miss là claim hợp lệ, không phải "claim cài" theo P:T12, nên tỉ lệ cài vẫn là 10/42.
- **Ước lượng** số claim tự nhiên ngẫu nhiên rơi vào dải: 30 × 5,8% + 30 × 8,9% + 31 × 13,5% + 31 × 12,4% ≈ 1,7 + 2,7 + 4,2 + 3,8 ≈ **12 claim** `none`. Đây là FP clustering tự nhiên (Q3: báo cáo tách riêng).

### 4.6 Ngày

- Provider thường: rải đều trên 366 ngày.
- Bệnh viện chỉ làm ngày thường: rải đều trên các ngày thứ Hai–thứ Sáu.
- `is_weekend` luôn tính từ ngày, nên đọc lại cho 0 cảnh báo. *(P:T12)*

### 4.7 Gán `claim_id` (D14)

- Sắp toàn bộ claim theo ngày. Trong cùng một ngày, xếp theo một **hoán vị ngẫu nhiên** (seeded). Sau đó sửa một ràng buộc duy nhất: bản sao duplicate luôn đứng sau bản gốc.
- Gán `CLM-00001`… theo thứ tự đó. *(D14; cách phá hoà là **Mới**)*
- Lý do không phá hoà theo thứ tự sinh: nếu theo thứ tự sinh, claim gian lận (sinh sau) luôn có `claim_id` lớn hơn claim hợp lệ cùng ngày. Thứ tự này không ảnh hưởng engine, nhưng là một tín hiệu lộ nhãn trong dữ liệu nộp.

## 5. Tám pattern gian lận

**Quy tắc chung cho mọi claim gian lận** (D12, Q4):
- Mỗi claim gian lận mang **đúng một** pattern. Chồng lấn được tránh **ngay từ cách sinh**: generator không gọi engine, không sinh lại dataset, không lọc sau khi sinh.
- Chọn member và ngày sao cho, trong dữ liệu generator đang giữ, không có claim nào khác cùng member và cùng chẩn đoán trong khoảng ±7 ngày. Ngoại lệ là chính các pattern duplicate và rapid, vốn cần claim gốc. Việc chọn có thể phải thử lại vài lần trong lúc sinh: xem **câu hỏi G2**.
- Số tiền dùng nhiễu cắt chặt hơn, m ≤ 1,25 (khoảng +1,67σ), để không vô tình vượt ngưỡng upcoding, và không rơi vào dải 47.500–49.999 (trừ pattern clustering). Ngoại lệ là upcoding và clustering. Xem **câu hỏi G7**.
- Không đặt claim gian lận nào (trừ clustering) vào 4 nhóm chứa dải clustering. *(**Mới**, giữ ràng buộc ≤ 25%)*
- Tại bệnh viện chỉ làm ngày thường, mọi claim gian lận khác weekend anomaly đều vào ngày thường (mục 2).
- Sau khi sinh, một test chạy engine và **chỉ báo cáo** số claim gian lận khớp từ 2 rule trở lên. *(Q4)*

| Pattern | Số | Cách sinh | Tránh chồng lấn và ràng buộc riêng | Nguồn |
|---|---:|---|---|---|
| **duplicate_claim** | 30 | Chọn 30 claim `none` làm bản gốc. Bản sao chép **y nguyên** member, provider, ngày, chẩn đoán, procedure và số tiền. Nhãn duplicate chỉ gắn cho bản sao; `related_claim_id` = bản gốc | Bản gốc phải: có m ≤ 1,25; số tiền ngoài dải clustering; không thuộc 4 nhóm clustering; không có claim nào khác cùng member và chẩn đoán trong ±7 ngày (để bản sao không dính rapid) | Đề, D9, R1, Q15, P:T13 |
| **rapid_resubmission** | 30 | Chọn 30 claim `none` làm claim trước. Claim nộp lại cùng member và chẩn đoán, ngày = gốc + U{1..7} (vẫn trong 2024). Provider giữ nguyên với xác suất 70%, ngược lại chọn provider khác cùng tầng có phục vụ claim_type đó. Template hợp lệ cho chẩn đoán; số tiền là nhiễu mới (xem G8). `related_claim_id` = claim trước | Cách ≥ 1 ngày nên không thành duplicate. Nếu ngày rơi vào cuối tuần mà provider là bệnh viện chỉ làm ngày thường thì chọn provider khác. Claim trước phải không có claim cùng member và chẩn đoán nào khác trong cửa sổ | Đề, D9, R2, Q15 |
| **upcoding** | 30 | Claim **một procedure** mới. **20 claim** có số tiền = giá gốc × U(1,5; 2,5); **10 claim** có số tiền = giá gốc × U(1,2; 1,6). Lý do của nhóm nhẹ: với hệ số 1,5–2,5 và CV 15%, mọi claim đều có z ≳ 3,3, nên recall gần 100% chỉ vì mức tăng tự đặt quá lớn. Hệ số dưới khoảng 1,3 cho z < 2, tức không bị bắt; khoảng 1/4 nhóm nhẹ (2–3 claim) rơi vào vùng này, chưa kể masking. Recall riêng của upcoding được báo cáo tách riêng | Chỉ nhắm nhóm chữ ký có ≥ 10 claim hợp lệ (thực tế: các nhóm có ≥ 30 claim), không phải 4 nhóm clustering, **tối đa 3 claim upcoded mỗi nhóm** (giảm masking, L9). Với giá gốc ≥ 15.000, nếu số tiền rơi vào dải clustering thì lấy lại hệ số (P-2101 và P-2102 có thể rơi vào). Ví dụ 30.000 × 1,6 = 48.000 | Q1, Q2, (c)1, P:T13 |
| **unbundling** | 25 | Chọn bundle, đặt chẩn đoán đúng của bundle: P-1101/E11.9, P-1102/I10, P-1103/N39.0, P-2101/K35.8, P-2102/K80.2, P-3101/K04.0. Procedure = **đủ** các thành phần. Số tiền = Σ giá thành phần × m. Biến thể "mã gói + thành phần": xem G9 | Số tiền ≤ 47.000 (chỉ ảnh hưởng P-2102, tổng thành phần 37.000). Thành phần nằm trong whitelist của chẩn đoán nên không dính mismatch. Claim nhiều procedure nên upcoding bỏ qua. Bundle phẫu thuật tại bệnh viện chỉ làm ngày thường thì vào ngày thường | R5, (c)2, L5 |
| **phantom_billing** | 25 | Một phòng khám tầm trung, một ngày thường mà provider đó **không có claim nền nào**. 25 claim gian lận (member khác nhau, template OUTPATIENT bình thường) cộng **đúng 6** claim `legit_noise`, tổng 31 | Ngày được chọn sao cho phòng khám không có claim nào khác: tổng đúng 31 > 30. Không đặt claim gian lận khác vào provider-ngày này | D10-B, Q8 |
| **weekend_anomaly** | 20 | Thứ Bảy hoặc Chủ Nhật, tại 8 bệnh viện chỉ làm ngày thường: 4 nơi × 3 + 4 nơi × 2. Template phẫu thuật nội trú: P-2101/K35.8, P-2102/K80.2, P-2012/S72.0, P-2013/M17.1 | Không dùng P-2009 (thuộc nhóm clustering). Với 65 claim nền + 3 claim gian lận cuối tuần = 68 claim, mỗi claim thấy 2/67 = 3,0% < 5% | Đề, D11, Q12, Q13 |
| **diagnosis_procedure_mismatch** | 20 | Lấy một template hợp lệ, thay **một** procedure bằng procedure cùng `claim_type` nhưng không hợp lệ với chẩn đoán | Procedure thay vào không được: tạo thành đủ bộ thành phần của một bundle; là mã gói của một thành phần đang có; là P-2009/P-2010; là procedure phẫu thuật nếu ngày rơi vào cuối tuần tại bệnh viện chỉ làm ngày thường | Q14, Q15 |
| **amount_clustering** | 20 | Số tiền ~ U{47.500..49.999}, chia đều 4 nhóm clustering (5 mỗi nhóm) | Claim có P-2009 (phẫu thuật) vào ngày thường, hoặc ở provider không thuộc nhóm chỉ làm ngày thường. Nhóm một procedure có μ ≈ 40–42 nghìn, nên z ≤ 1,3, không dính upcoding | Q3, Q15, P:T14 |

## 6. Nhiễu và hard negatives (D13, Q3, Q7)

### 6.1 `legit_noise` (claim hợp lệ cài để kích hoạt rule)

| Kịch bản | Số | Rule bị kích hoạt | Nguồn |
|---|---:|---|---|
| Số tiền ~ U{47.500..49.999} trong 4 nhóm clustering (5 mỗi nhóm) | 20 | amount_clustering | Q3 |
| Claim hợp lệ cùng provider-ngày phantom | 6 | phantom_billing | Q8 |

### 6.2 `legit_near_miss` (sát biên, không kích hoạt rule)

Có khoảng 25 claim, cộng kịch bản riêng ngày 30 claim (Q7).

| Kịch bản | Số | Sát biên của | Nguồn |
|---|---:|---|---|
| Số tiền 47.000–47.499 trong nhóm clustering (nằm trong quota 32, mục 4.5) | 3 | R9 (47.499 không flag) | D13 |
| Số tiền 50.000–50.500 trong nhóm clustering (nằm trong quota 32, mục 4.5) | 3 | R9 (50.000 không flag) | D13 |
| Cùng member và chẩn đoán, cách đúng 8 ngày | 4 | R2 (8 ngày không flag) | D13 |
| Cùng member, provider, ngày nhưng **khác chẩn đoán** | 3 | R1 | **Mới** |
| Phẫu thuật cuối tuần tại bệnh viện thường (tỉ lệ cuối tuần khoảng 28%) | 4 | R7 | D13 |
| Một phần thành phần bundle: I10 P-1005 + P-1006; E11.9 P-1004 + P-1006; N39.0 P-1003 | 4 | R5 | D13 |
| Claim một procedure với m ∈ [1,25; 1,28], khoảng +1,7σ đến +1,9σ | 4 | R3 (z gần 2 nhưng không vượt) | D13 |
| **Kịch bản riêng:** một phòng khám có **đúng 30** claim hợp lệ trong một ngày thường, không có claim nào khác trong ngày | 30 | R6 (30 không flag) | Q7 |

**Lưu ý:** near-miss upcoding không bảo đảm z < 2. Engine tính z trên dữ liệu thật của nhóm, nên một vài claim có thể vượt 2. Nếu xảy ra, đó là FP thật và được báo cáo trung thực, không chỉnh lại.

## 7. `ground_truth.csv` (D3, Q7)

| Cột | Giá trị |
|---|---|
| `claim_id` | trùng `claims.csv` |
| `is_fraud` | `true` cho 8 pattern, `false` cho các nhãn còn lại |
| `fraud_pattern` | một trong 8 id rule (O5), hoặc `none`, `legit_near_miss`, `legit_noise` |
| `related_claim_id` | duplicate → bản gốc; rapid → claim trước; near-miss 8 ngày và near-miss khác chẩn đoán → claim cặp; các trường hợp khác để trống |
| *(đề xuất)* `scenario` | Mã kịch bản chi tiết (ví dụ `phantom_day`, `day_30_claims`, `near_miss_rapid_8_days`, `clustering_band_noise`), để báo cáo near-miss/noise theo từng loại (M2). Đây là cột thứ 5, **lệch D3**: xem G5 |

Ghi bằng `"\n"`, UTF-8 không BOM, thứ tự dòng theo `claim_id`. Chỉ module `metrics` được đọc file này.

## 8. Độc lập với engine (H2#1, D3)

**Quy tắc:**
- Package `com.papaya.fraud.generator` **không import** `com.papaya.fraud.engine..` và `com.papaya.fraud.config..`, và không đọc `fraud-config.json`.
- Generator **được** dùng `catalog` (reference data dùng chung, D5), `model.Claim` và `io.ClaimCsvWriter`.
- Mọi tham số của generator nằm trong `src/main/resources/generator-config.json`, với **tên khóa khác** tên tham số của engine. Ví dụ `resubmission_gap_days` thay vì `window_days`, và `planted_amount_range` thay vì `lower_bound`/`upper_bound`. Một số giá trị trùng với tham số engine (7 ngày, dải 47.500–49.999), vì cả hai cùng lấy từ đề, nhưng chúng được khai báo hai lần độc lập.
- Engine, `io`, `config`, `model` và lệnh `score` không tham chiếu ground truth.

**Phác thảo `generator-config.json`** (tên khóa là đề xuất):
```json
{
  "dataset": { "total_claims": 2000, "member_count": 500, "year": 2024,
               "provider_tiers": { "weekday_only_hospitals": 8, "regular_hospitals": 4, "clinics": 28, "dental_clinics": 10 } },
  "volumes": { "claim_type_mix": { "OUTPATIENT": 0.55, "INPATIENT": 0.25, "DENTAL": 0.20 },
               "weekday_only_hospital_claims": 65, "band_host_natural_claims": 32, "chronic_member_share": 0.20 },
  "pricing": { "noise_cv": 0.15, "noise_truncation_sigmas": 3, "fraud_noise_cap": 1.25 },
  "fraud_counts": { "duplicate": 30, "resubmission": 30, "upcode": 30, "unbundle": 25, "phantom": 25,
                    "weekend_surgery": 20, "mismatch": 20, "band_amount": 20 },
  "upcode": { "factor_min": 1.5, "factor_max": 2.5, "max_per_group": 3, "min_group_claims": 10 },
  "resubmission": { "gap_days_min": 1, "gap_days_max": 7, "same_provider_share": 0.7 },
  "unbundle": { "amount_cap": 47000 },
  "planted_amount_range": { "min": 47500, "max": 49999 },
  "phantom_day": { "fraud_claims": 25, "legit_claims": 6 },
  "noise": { "band_legit_claims": 20, "near_miss": { "...": "theo bảng 6.2" }, "day_30_claims": 30 }
}
```

**Test kiến trúc đề xuất** (Task 12, bằng cách quét source trong `src/main/java`, không cần thư viện mới):
1. `GeneratorIndependenceTest`:
   - Không file nào dưới `generator/` chứa `import com.papaya.fraud.engine`, `import com.papaya.fraud.config` hoặc chuỗi `fraud-config`.
   - Ngược lại, không file nào dưới `engine/`, `config/`, `io/` chứa `com.papaya.fraud.generator`.
   - Tập khóa (đệ quy) của `generator-config.json` và `fraud-config.json` **không giao nhau**.
2. `GroundTruthIsolationTest`: không file nào dưới `engine/`, `config/`, `io/`, `model/`, `catalog/`, cùng `cli/ScoreCommand.java`, chứa `GroundTruth`, `ground_truth`, `is_fraud` hoặc `fraud_pattern`. Bổ trợ cho test đã có: header có `is_fraud` bị reader từ chối.

## 9. Quyết định (đã chốt ngày 2026-10-25)

- **G1:** RNG con theo giai đoạn, tạo bằng `SplittableRandom` với seed trộn bằng hàm băm (mục 1). Không dùng công thức `seed × 31 + mã`.
- **G2:** được rút lại lựa chọn trong lúc sinh, tối đa 1.000 lần, chỉ dựa trên ràng buộc của generator, không bao giờ dựa trên kết quả rule của engine. Ghi rõ vào writeup.
- **G3–G14:** đồng ý theo đề xuất trong bảng dưới.
- **Bổ sung:** upcoding chia 20 claim mạnh U(1,5; 2,5) và 10 claim nhẹ U(1,2; 1,6) (mục 5). 6 near-miss quanh dải clustering nằm trong quota 32 (mục 4.5).

Bảng câu hỏi gốc, giữ lại để truy vết:

| # | Câu hỏi | Đề xuất |
|---|---|---|
| **G1** | RNG: **một** `Random(seed)` duy nhất gọi theo thứ tự cố định (đúng như bạn nêu), hay **mỗi giai đoạn một RNG con** với seed suy ra từ seed gốc (ví dụ `seed × 31 + mã giai đoạn`)? | **RNG con theo giai đoạn.** Vẫn tất định, và thêm hoặc bớt near-miss không làm đổi toàn bộ dữ liệu nền. Nếu muốn đúng một RNG thì vẫn làm được |
| **G2** | Khi chọn member, ngày hoặc provider cho claim gian lận mà vi phạm ràng buộc (ví dụ đã có claim cùng chẩn đoán trong ±7 ngày), generator có được **rút lại lựa chọn đó ngay trong lúc sinh** không? Đây không phải lọc sau khi sinh và không gọi engine | **Có**, giới hạn 1.000 lần thử; quá giới hạn thì ném lỗi rõ ràng. Ghi vào writeup để phân biệt với "lọc lại" mà Q4 cấm |
| **G3** | Có sinh tái khám tự nhiên trong ≤ 7 ngày cho member mạn tính không (rapid hợp lệ thật, nhãn `none`)? D13 đang để phần này là hướng mở rộng | **Có, ít:** khoảng 3% claim của member mạn tính có một lần tái khám sau 2–7 ngày (khoảng 10–15 claim). Rapid sẽ có FP thật thay vì gần 0 |
| **G4** | Số provider theo tầng (8 + 4 bệnh viện, 28 phòng khám, 10 nha khoa) và cơ cấu claim_type 55/25/20 | Như bảng 2 và 4.2 |
| **G5** | Thêm cột thứ 5 `scenario` vào `ground_truth.csv` (lệch D3), hay giữ 4 cột? | **Thêm.** Không có cột này thì không báo cáo được near-miss và noise theo từng loại (M2). Engine vẫn không đọc file này |
| **G6** | 4 nhóm clustering chiếm 168 claim (8,4%) do quota 32 + 10. Q3 nói "không chiếm tỉ lệ lớn": 8,4% có chấp nhận được không? | **Chấp nhận.** Đây là hệ quả số học của "≥ 30 claim tự nhiên" cộng "claim cài ≤ 25%" |
| **G7** | Claim gian lận (trừ upcoding và clustering) và bản gốc của duplicate/rapid dùng nhiễu cắt ở m ≤ 1,25 thay vì 1,45, để không vô tình vượt z = 2. Hệ quả: số tiền của chúng hơi ít biến động hơn claim hợp lệ | **Chấp nhận**, và ghi vào `limitations.md`. Engine không dùng đặc điểm này |
| **G8** | Số tiền của claim rapid: nhiễu mới (m ≤ 1,25), hay copy số tiền của claim trước? Q15 chưa nói | **Nhiễu mới.** Copy số tiền thì giống duplicate hơn là nộp lại |
| **G9** | Trong 25 claim unbundling, có bao nhiêu claim là biến thể "mã gói + thành phần" (thanh toán kép, R5)? | **20 claim đủ thành phần + 5 claim thanh toán kép**, để cả hai nhánh của rule có trong dữ liệu |
| **G10** | Nhãn của 30 claim ngày 30 claim là `legit_near_miss` (tính riêng ngoài ngân sách khoảng 25, phân biệt bằng cột `scenario`)? | **Đúng như vậy** |
| **G11** | Upcoding: tối đa 3 claim mỗi nhóm, chỉ nhắm nhóm có ≥ 30 claim hợp lệ (chặt hơn ngưỡng 10 của P:T13), để giảm masking | **Chấp nhận** |
| **G12** | Xáo việc gán `provider_id` theo tầng, và phá hoà `claim_id` trong cùng ngày bằng hoán vị ngẫu nhiên (mục 2 và 4.7) | **Chấp nhận**, để dữ liệu nộp không lộ tầng provider hay nhãn qua thứ tự id |
| **G13** | Kịch bản near-miss ở bảng 6.2: danh sách và số lượng | Như bảng: 25 claim, cộng 30 claim ngày 30 claim |
| **G14** | Chia Task 12–15 có giữ như kế hoạch không? (12: nền + provider + member + id + ghi file; 13: duplicate, rapid, upcoding, unbundling; 14: phantom, weekend, mismatch, clustering; 15: noise và near-miss) | **Giữ.** Riêng 4 nhóm clustering và ngày phantom/ngày 30 claim được **giữ chỗ** từ Task 12 (quota và chọn provider-ngày), để các claim gian lận ở Task 13–14 không vô tình đặt vào đó |
