# PROGRESS

Cập nhật lần cuối: 2026-10-25.

## Trạng thái

| # | Task | Trạng thái | Commit |
|---|------|------------|--------|
| 01 | Skeleton Maven, CLI stub, limitations, ai-log | done | `eb2fc4b` |
| 02 | Danh mục mã (4 file JSON) và `CatalogLoader` | done | `d8b8e1e` |
| 03 | Config (`fraud-config.json`, `ConfigLoader`) | done | `205b36e` |
| 04 | Đọc/ghi CSV và validate đầu vào (R11) | done | `98cb920` |
| 05 | `DatasetIndex`, `Rule`, `Flag`, fixture builder | done | `7bda7e6` |
| 06 | Rule duplicate và rapid re-submission | done | `75c1ef6` |
| 07 | Rule upcoding | done | `9d6f6e6` |
| 08 | Rule unbundling và diagnosis-procedure mismatch | done | `ec09b94` |
| 09 | Rule phantom billing và amount clustering | done | `d3d62a7` |
| 10 | Rule weekend anomaly | done | `72d5303` |
| 11 | Chấm điểm, xếp hạng, `FraudEngine`, subcommand `score`, đo thời gian | done | `e79b499` |
| 12 | Generator: phần claim hợp lệ, subcommand `generate`, ground truth | done | `f73fa05` |
| 13 | Generator gian lận (1): duplicate, rapid, upcoding, unbundling | done | `4ad074b` |
| 14 | Generator gian lận (2): phantom, weekend, mismatch, clustering | done | `08cfdc6` |
| 15 | Near-miss và noise | done | `f6d7481` |
| 16 | Metric, subcommand `evaluate` và `tune` | done | `bae0be7` |
| 17 | Tuning (seed 42) và commit đóng băng `config/frozen-config.json` | done | `c93d367`, tag `frozen-before-heldout` |
| 18 | Held-out (seed 2024) và seed ổn định 101–105 | done | 18a `68e6309`, 18b `e2efa0d`, 18c `cfdf676`, 18d commit "Task 18" (mã điền ở lần cập nhật sau) |
| 19 | Writeup: README, interpretations, timeline, limitations, ai-log | todo | |

Commit ngoài kế hoạch:
- `d335972` Add .gitattributes to normalize line endings to LF.
- `c871aa9` Add CLAUDE.md and PROGRESS.md, update limitations.
- `f915033` Fix: use ﻿ escape in ClaimCsvReader.
- `045729e` Add generator spec and limitations L10-L12.

## Đang làm

Task 18: sau khi dừng ở bước 3, người dùng chọn phương án (a). Đã commit 18b (`e2efa0d`, bỏ đường dẫn đầu vào khỏi `metrics.json` và báo cáo, chỉ trình bày) và 18c (`cfdf676`, sinh lại báo cáo tuning theo định dạng mới, không đổi số). Sau đó `evaluate` và `all --seed 2024` cho cả bốn file (`claims.csv`, `ground_truth.csv`, `scored_claims.json`, `metrics.json`) giống hệt từng byte. Đã kiểm chứng bằng SHA-256 rằng không điểm hay metric nào đổi (chi tiết ở `limitations.md`, L16). Tag `frozen-before-heldout` vẫn ở `c93d367`.

Task 18 đã commit đủ (18a–18d), 192 test xanh kể cả `HeldOutIntegrationTest`. Chưa push. Bước tiếp theo: Task 19 (writeup). Kết quả:
- `HeldOutIntegrationTest` chạy riêng: **đạt** (recall 0,9650, FPR 0,0444, T = 7 đọc từ file).
- Held-out seed 2024, T = 7: TP 193, FP 80, FN 7, TN 1.720; recall 96,5%, FPR chuẩn 4,4% (PASS), FDR 29,3% (FAIL theo cách hiểu thứ hai), precision 70,7%. Recall thấp nhất: upcoding 76,7%.
- Seed 101–105 (`reports/stability/summary.md`, tính bằng `scripts/StabilitySummary.java`): recall trung bình 97,6% (SD 0,74 pp), FPR 4,2% (SD 0,37 pp), FDR 28,0% (SD 1,70 pp), precision 72,0% (SD 1,70 pp); recall thấp nhất của một pattern 76,7–90,0% (luôn là upcoding).

Dòng tóm tắt held-out đã thấy **trước** thay đổi trình bày: T = 7, recall 96,5%, FPR chuẩn 4,4%, FDR 29,3%, precision 70,7% (TP 193, FP 80, FN 7, TN 1.720).

Task 18a: đã viết `HeldOutIntegrationTest` (test #37) **trước** khi sinh hay chạy bất cứ thứ gì về held-out; commit riêng, chưa chạy test này. Các test khác: 189 test xanh khi loại trừ test held-out. Sửa một chỗ lệch Q19 (trước khi nhìn held-out): test chấm 100.000 claim trong `FraudEngineTest` có giới hạn **120 giây**, Q19 chốt **< 30 giây**; đã đổi thành 30 giây (lần chạy này: 2.414 ms).

Task 17 đã commit và gắn tag `frozen-before-heldout` (chưa push).

Task 17a: Đã sinh `data/tuning/` (seed 42; sinh lại cho SHA-256 giống hệt), chạy `tune` (T = 7) và `evaluate` (`reports/tuning/`). Không sửa rule, trọng số, tham số hay generator. Chưa sinh held-out (seed 2024) và seed 101–105.

Số **in-sample trên tuning** (không phải số nộp): T = 7 (T = 1..7 cho cùng TP/FP); TP 196, FP 60, FN 4, TN 1.740; recall 98,0%, FPR chuẩn 3,3% (PASS), FDR 23,4% (FAIL theo cách hiểu thứ hai), precision 76,6%. Recall mọi pattern ≥ 70% (thấp nhất upcoding 86,7%). Không chạm điều kiện dừng nào (recall tổng < 70%, pattern < 70%, FPR chuẩn > 5%). `frozen-config.json` chỉ khác `fraud-config.json` ở trường `threshold`.

Báo cáo kiểm tra seed 42 sau Task 15 (chỉ báo cáo, **không** dùng để chỉnh generator, **chưa phải số chính thức**): 256 claim bị flag. Gian lận bị flag bởi đúng rule: 196/200 (upcoding 26/30, phantom 25/25, các pattern khác đủ). Hợp lệ bị flag: 60 = band_noise 20 + phantom_day_legit 6 (đều cố ý) + band_host 7 + base 15 + natural_followup 12. Không near-miss nào bị flag.

Báo cáo kiểm tra seed 42 sau Task 14 (chỉ báo cáo theo Q4, **không** dùng để chỉnh generator), số claim bị flag bởi đúng rule / tổng: duplicate 30/30, rapid 30/30, upcoding 26/30, unbundling 25/25, **phantom 0/25** (ngày phantom mới có 25 claim, chưa vượt 30; Task 15 thêm 6 claim `legit_noise`), weekend 20/20, mismatch 20/20, clustering 20/20. Không claim gian lận nào khớp từ 2 rule trở lên. Claim hợp lệ bị flag: 34 (upcoding 9, rapid 18, clustering 7).

Báo cáo masking sau Task 13 (chỉ báo cáo): 4 claim upcoding bị bỏ sót đều là claim nhẹ. Số claim hợp lệ bị upcoding flag giảm từ 34 (dữ liệu nền) xuống 9, vì các claim upcoded làm phình SD của chính nhóm chúng (masking tác động lên cả FP tự nhiên).

Báo cáo chồng lấn seed 42 sau Task 13 (chỉ báo cáo theo Q4, **không** dùng để chỉnh generator): duplicate 30/30 bị flag bởi đúng rule, rapid 30/30, unbundling 25/25, upcoding 26/30 (4 claim nhẹ không bị bắt); không claim gian lận nào khớp từ 2 rule trở lên.

Chạy thử seed 42 trên dữ liệu nền của Task 12 (1.719 claim, toàn bộ hợp lệ; chỉ để kiểm tra, không dùng để chỉnh gì): 59 claim bị flag, gồm upcoding 34, rapid 18, clustering 7; các rule khác 0.

Thời gian đo được ở lần chạy test Task 11 (máy dev, chỉ để tham khảo, chưa phải số báo cáo): 2.000 claim chấm và serialize trong 149 ms; 100.000 claim chấm trong 4.267 ms.

## Việc tiếp theo

1. Điền mã commit Task 17 vào bảng trạng thái.
2. Task 18: held-out seed 2024 và seed ổn định 101–105, với config đã đóng băng; không chỉnh gì sau khi xem kết quả.
3. Task 19: writeup.

## Câu hỏi còn mở / chờ quyết

- [ ] **Q5**: chờ người dùng gửi nội dung để điền vào phụ lục.
- [ ] **README mục lục ở gốc `papaya-insurtech/`**: người dùng nói "thêm sau", chưa gắn vào task nào.

## Quyết định đã chốt trong phiên làm việc

- 2026-10-25: Người dùng điền phần "Quyết định" và "Trọng số" trong `design-decisions.md`.
- 2026-10-25: Trả lời Q1–Q24. Q2–Q4 và Q6–Q24 đã ghi vào phụ lục; Q1, Q5 chờ nội dung.
- 2026-10-25: Duyệt kế hoạch 19 task, không cắt task; dùng sáng 28/10 làm buffer.
- 2026-10-25: Gốc git chuyển về `papaya-insurtech/`. Đã xoá repo con rỗng trong `AI_Engineering_Challenges/`.
- 2026-10-25: Làm thẳng trên `main`, mỗi task một commit, không amend commit Task 01.
- 2026-10-25: Danh tính git đặt cho repo: `Ngoc Tran Le Que <tlqngoc12@gmail.com>`.
- 2026-10-25: `Logical_Questions/README.md` commit chung với Task 01.
- 2026-10-25: `docs/interview-defense.md` không đưa vào repo (người dùng tự chuyển ra ngoài).
- 2026-10-25: Thêm `.gitattributes` (`* text=auto eol=lf`, binary cho jar/png/jpg), commit riêng.
- 2026-10-25: Log AI giữ nguyên văn file `.txt` từ `/export` trong `docs/ai-log/`, không chuyển đổi.
- 2026-10-25: Tạo `CLAUDE.md` và `docs/PROGRESS.md`. Ghi chú làm việc chỉ để ở `PROGRESS.md`; `README.md` chỉ dành cho người chấm.
- 2026-10-25: Task 02, ràng buộc generator:
  - (c)1: với **mọi** procedure có giá gốc ≥ 15.000, nếu số tiền upcoding rơi vào dải clustering thì lấy lại hệ số. Upcoding chỉ nhắm procedure (nhóm chữ ký) có ít nhất 10 claim.
  - (c)2: nhiễu claim unbundled cắt ≤ 47.000. Đã ghi vào `limitations.md` (L5).
  - (c)3: claim hợp lệ luôn dùng mã gói.
  - (c)4: mã chung chỉ xuất hiện như claim một procedure.
- 2026-10-25: Task 02, giữ cặp J06.9 + P-1002. P-1001 thêm INPATIENT (dùng cho K35.8).
- 2026-10-25: Task 02, `CatalogLoaderTest` phải tự đếm số cặp whitelist và kiểm tra mỗi cặp hợp lệ về claim_type.
- 2026-10-25: Task 02, duyệt danh mục.
  - P-0002 giữ claim_type O, D.
  - Mã chung chỉ hợp lệ với chẩn đoán có cùng claim_type: P-0001 với 21 chẩn đoán, P-0002 với 14 chẩn đoán (có O hoặc D), tổng 35 cặp mã chung.
  - PCI (P-2011) và coronary angiography (P-2010) giữ `surgical = false`. Ghi vào `limitations.md` (L6).
- 2026-10-25: Task 02, sức chứa dải clustering: chọn **phương án 3**. 4 nhóm chứa claim trong dải: P-2009, P-2010, I21.9 (P-2010 + P-2001), O82 (P-2009 + P-2001).
- 2026-10-25: Thứ tự commit: `.gitattributes` (đã có ở `d335972`), rồi `CLAUDE.md` + `PROGRESS.md` + `limitations.md`, rồi Task 02 chỉ gồm danh mục.
- 2026-10-25: Task 02, thử phá có chủ ý 3 lần (giá P-2008, bỏ thành phần của P-2102 khỏi K80.2, thêm E11.9 + P-3003): cả 3 lần đều có test đỏ.
  - Không sửa `CatalogLoaderTest` để đọc JSON thô, vì `Catalog.create` đã chặn lỗi với thông báo chính xác và `CatalogTest` đã kiểm tra từng loại lỗi.
  - Không thêm snapshot test. Ghi lỗ hổng "tráo cặp" vào `limitations.md` (L7).
  - Commit Task 02 gồm danh mục, test, `limitations.md` và `PROGRESS.md`.
- 2026-10-25: Task 03, config.
  - `fraud-config.json` không chứa T. T chỉ có trong `config/frozen-config.json`, do `tune` ghi ra (cùng schema, thêm `threshold`).
  - `score` không cần T. Lỗi thiếu T (`requireThreshold()`) chỉ xảy ra ở `evaluate` và `all`. Không có T mặc định.
  - Thêm `rules.amount_clustering.auto_approval_threshold = 50000`, kiểm tra `upper_bound < auto_approval_threshold`. Evidence clustering lấy con số 50.000 từ trường này. Cả ba biên clustering đọc thành `long`, có test với giá trị vượt `Integer.MAX_VALUE`.
  - Ràng buộc tham số: `min_group_size ≥ 2`, `window_days ≥ 1`, `max_weekend_ratio` trong (0; 1), `threshold` 1–100.
- 2026-10-25: Task 04, đọc/ghi CSV và validate.
  - `claim_id` trùng: giữ dòng đầu, loại các dòng sau.
  - Header sai: không đọc file, trả về 1 dòng bị loại ở dòng 1. Thông báo nêu rõ cột lạ và cột thiếu. Header có thêm `is_fraud` bị từ chối (bảo vệ ràng buộc "engine không đọc ground truth").
  - `procedure_codes` có mã rỗng (ví dụ `P-1001|`): loại dòng.
  - Bắt buộc khác rỗng: `claim_id`, `member_id`, `provider_id`, `claim_date`, `claim_type`, `diagnosis_code`, `submitted_amount`. `provider_name` được rỗng; `is_weekend` rỗng chỉ cảnh báo.
  - `is_weekend` không phân biệt hoa thường (Excel lưu `TRUE`/`FALSE`); `claim_type` phải viết hoa đúng.
  - CSV hỏng cấu trúc: dừng đọc, giữ các dòng đã đọc trước đó. Số dòng là dòng bắt đầu của row bị hỏng; thông báo kèm dòng nơi parser dừng (Jackson chỉ phát hiện thiếu nháy đóng ở cuối file).
  - Không giới hạn `claim_date` trong năm 2024.
- 2026-10-25: Task 05, `DatasetIndex`.
  - Đếm cuối tuần dùng `Claim.isWeekend()` (reader đã tính lại theo R11), không tính lại lần hai.
  - Chữ ký procedure = các mã khác nhau, sắp xếp, nối bằng `|`; mã lặp trong một claim tính là một.
  - `DatasetIndex.of` ném `IllegalArgumentException` khi `claim_id` trùng (lỗi lập trình, reader đã loại trước).
  - `weekendRatioExcluding` trả rỗng khi provider không còn claim nào khác; ném lỗi khi claim không thuộc index.
  - `Rule` là interface thường, seal ở Task 10.
- 2026-10-25: Task 06, duplicate và rapid.
  - Duplicate khớp đúng 4 trường của đề (member, provider, date, diagnosis); flag mọi claim trừ claim đầu theo (`claim_date`, `claim_id`).
  - Rapid: có ít nhất một claim trước đó cùng member và chẩn đoán trong `window_days` (≤ 7), bất kể provider, không tính cặp đã là duplicate của chính claim đó. Evidence trích claim gần nhất.
  - Bản sao duplicate có thể nhận thêm rapid nếu còn claim khác cùng member và chẩn đoán trong cửa sổ: **giữ nguyên rule** (phương án a), xử lý ở generator (xem ràng buộc Task 13).
  - Evidence rapid dùng đúng số ít/số nhiều ("1 earlier claim", "2 earlier claims").
- 2026-10-25: **Q1 = A** (ghi đè R3, đã ghi vào phụ lục `design-decisions.md`): upcoding chỉ xét claim có đúng một procedure; claim nhiều procedure bị bỏ qua và được đếm.
- 2026-10-25: Task 18, quy trình:
  - Trước khi nhìn held-out: đổi giới hạn test 100.000 claim từ 120 s xuống 30 s cho đúng Q19 (`68e6309`).
  - Sau khi đã thấy dòng tóm tắt held-out (recall 96,5%, FPR 4,4%): thay đổi **chỉ về trình bày** (`e2efa0d`, `cfdf676`). `metrics.json` chỉ giữ SHA-256 của đầu vào; `metrics_report.md` ghi tên file, không đường dẫn. Đã kiểm chứng bằng SHA-256 rằng không điểm hay metric nào đổi (L16).
- 2026-10-25: Task 17, quyết định trên tuning seed 42 (đã ghi nguyên văn vào `design-decisions.md`, mục S3 và R4):
  - T = 7. Lý do: Giữ tiêu chí đã chốt trước khi chạy: T là giá trị cho recall cao nhất với FPR chuẩn <= 20% (đúng chữ của đề). Trên tuning T = 7 cho recall 98,0%, FPR 3,3%. FDR 23,4% không đạt nếu hiểu "FPR" theo nghĩa tỉ lệ cảnh báo sai; mình báo cáo cả hai cách hiểu thay vì đổi tiêu chí. T = 11 qua cả hai cách hiểu (FDR 9,3%) nhưng recall còn 73,0% (biên 3 điểm), rapid và clustering không bao giờ được bắt khi đứng một mình, và vi phạm mục tiêu nội bộ recall >= 85%.
  - R4: giữ R4-A (z-score, thống kê gồm cả claim đang xét). Lý do: Tiêu chí đổi sang median/MAD đặt trước là recall upcoding < 70% trên tuning; thực tế 86,7% (26/30) nên không kích hoạt. Bốn claim sót là ba claim tăng nhẹ và CLM-01130 bị masking, là giới hạn đã biết của z-score (L9). Đổi rule sau khi đã thấy số là chỉnh cho đẹp.
  - Thêm L13 (weekend dựa trên 40 claim), L14 (near-miss 0/55 chỉ chứng minh tính nhất quán), L15 (rapid và clustering cần T ≤ 7) vào `limitations.md`.
- 2026-10-25: Task 16, metric và lệnh `tune`/`evaluate`/`all`.
  - Dự đoán gian lận khi `risk_score >= T`. Báo cáo cả FPR chuẩn (FP / số claim hợp lệ) và FDR (FP / số claim bị flag), kèm định nghĩa (M1).
  - Chọn T (S3): trong các T có FPR ≤ 20%, recall cao nhất, rồi FPR thấp nhất, rồi T lớn nhất; so sánh trên số đếm. `tune` báo lỗi và không ghi file nếu không T nào đạt FPR ≤ 20%, hoặc nếu T tốt nhất có TP = 0.
  - `tune` ghi `frozen-config.json` và `threshold-sweep.json` cạnh nó; không ghi đè trừ khi có `--force`.
  - `evaluate` và `tune` dừng nếu có dòng claim bị loại, hoặc nếu claims và ground truth không khớp `claim_id` theo cả hai chiều.
  - `score` và `evaluate` dùng chung `pipeline.ScoringPipeline`, nên `scored_claims.json` giống hệt từng byte (có test).
  - Thời gian load/score/write chỉ nằm trong `metrics_report.md`, ở mục riêng "Timing (varies from run to run)". `metrics.json` không chứa giá trị nào đổi theo lần chạy và giống hệt từng byte giữa hai lần chạy cùng dữ liệu (có test).
  - `evaluate --seed <n>` là tùy chọn, chỉ để ghi vào báo cáo ("not given" nếu thiếu); `all` tự điền seed.
  - Liên kết tới `docs/limitations.md`: đường dẫn tương đối từ thư mục output, tuyệt đối nếu khác ổ đĩa.
  - Ví dụ chọn T trên fixture 6 claim của test. Gian lận: CLM-00004 (25 điểm), CLM-00002 (18), CLM-00003 (14), CLM-00006 (11). Hợp lệ: CLM-00005 (7), CLM-00001 (0). T = 1..7 flag thêm CLM-00005, FPR 1/2 = 50%, bị loại. T = 8..11 bắt đủ 4 claim gian lận, FP = 0; hoà nên chọn T lớn nhất là **11**. T = 12 bỏ sót CLM-00006.
- 2026-10-25: Task 15, noise và near-miss: 20 `band_noise` (5 mỗi nhóm), 6 `phantom_day_legit` (ngày phantom đủ 31 claim), 25 near-miss theo 7 scenario (6 claim quanh dải nằm trong quota 32 của nhóm), 30 `day_30_claims`. Tổng đúng 2.000 claim; generator kiểm tra tổng khi kết thúc.
- 2026-10-25: Task 14, generator gian lận (2): phantom 25 claim tại ngày giữ chỗ (member khác nhau); weekend 20 claim (4 × 3 + 4 × 2) phẫu thuật nội trú cuối tuần tại bệnh viện chỉ làm ngày thường, không P-2009; mismatch 20 claim thay đúng một procedure; clustering 20 claim, 5 mỗi nhóm. Thêm L9 mở rộng (masking giảm cả FP tự nhiên).
- 2026-10-25: Task 13, generator gian lận (1): duplicate (copy y nguyên, bản sao đứng sau bản gốc), rapid (1–7 ngày, 70% cùng provider, nhiễu mới), upcoding (20 mạnh + 10 nhẹ, ≤ 3 mỗi nhóm, nhóm ≥ 30 claim, không phải nhóm clustering), unbundling (20 đủ thành phần + 5 thanh toán kép). Claim gốc của duplicate/rapid phải "cô lập" (không có claim cùng member + chẩn đoán trong ±7 ngày); claim gian lận mới đặt sao cho không có claim cùng member + chẩn đoán trong ±7 ngày.
- 2026-10-25: Task 12, generator phần nền: member gán trước chẩn đoán (member mạn tính quyết định chẩn đoán); claim tái khám không đặt ở bệnh viện chỉ làm ngày thường. Spec 4.1 đã cập nhật cho khớp.
- 2026-10-25: Duyệt `docs/generator-spec.md`: G1 (RNG con bằng `SplittableRandom`, seed trộn bằng hàm băm), G2 (rút lại lựa chọn ≤ 1.000 lần, chỉ theo ràng buộc của generator), G3–G14 theo đề xuất; upcoding 20 claim mạnh + 10 claim nhẹ; 6 near-miss quanh dải clustering nằm trong quota 32. D3 có thêm cột `scenario` (ghi vào phụ lục `design-decisions.md`). Thêm L10–L12 vào `limitations.md`.
- 2026-10-25: Task 11, chấm điểm và lệnh `score`.
  - Điểm tính bằng số nguyên: `(200·Σw + W) / (2·W)` = round half-up của 100·Σw/W (S1-A, S7).
  - Xếp hạng S5; flag trong mỗi claim theo thứ tự `RuleId`.
  - `scored_claims.json`: pretty printer riêng, thụt 2 dấu cách, `"\n"`, `"key": value`, mảng rỗng là `[]`, có dòng trống cuối file.
  - Lệnh `score` không cần T; in số claim đọc, bị loại, cảnh báo, số claim chấm, số claim bị flag, thời gian load/score/write và coverage.
- 2026-10-25: Task 10, weekend anomaly.
  - Flag khi: claim cuối tuần, có ít nhất một procedure `surgical` trong catalog, lịch sử provider (loại claim đang xét) ≥ `min_history` (20), tỉ lệ cuối tuần leave-one-out < `max_weekend_ratio` (5%, nghiêm ngặt). Mã lạ không tính là phẫu thuật.
  - Thêm `DatasetIndex.volumeExcluding(claim)`; phần trăm cắt bớt chuyển vào `Evidence.percent`, dùng chung với clustering.
  - Seal `Rule`: chọn phương án (a), chuyển `Rule` sang `engine.rules` vì sealed type trong unnamed module chỉ cho phép class con cùng package (javac báo lỗi khi khác package).
- 2026-10-25: Task 09, phantom và clustering.
  - Phantom: > `max_claims_per_day` (30, nghiêm ngặt) thì flag mọi claim của provider-ngày; tra qua `DatasetIndex`.
  - Clustering: `lower_bound ≤ amount ≤ upper_bound` (47.500–49.999); evidence ghi phần trăm so với `auto_approval_threshold`, một chữ số thập phân, **cắt bớt** (49.999 hiện 99.9%).
  - Sửa ký tự U+FEFF thật trong `ClaimCsvReader.java` thành escape `'﻿'`, commit riêng.
- 2026-10-25: Task 08, unbundling và mismatch.
  - Unbundling: đủ mọi thành phần của một bundle (bất kể chẩn đoán), hoặc mã gói cùng bất kỳ thành phần nào. Thiếu thành phần thì không flag. Một flag mỗi claim, evidence liệt kê mọi bundle khớp kèm tổng giá gốc thành phần so với giá gói.
  - Nhãn evidence: "Unbundling", "Double billing" (mã gói + thành phần), hoặc "Unbundling and double billing" khi claim có cả hai loại. Rule và severity không đổi.
  - Mismatch: chỉ flag khi chẩn đoán có trong whitelist và có ít nhất một procedure đã biết không hợp lệ. Chẩn đoán lạ không flag; procedure lạ bị bỏ qua và đếm vào coverage.
- 2026-10-25: Task 07, upcoding: mean/SD tính cả claim đang xét (R4-A); nhóm < `n_min` hoặc SD = 0/NaN thì không flag; z > `z_threshold` nghiêm ngặt. Evidence ghi mean và SD với 1 chữ số thập phân, z với 2 chữ số, để người đọc tự tính lại được z.

## Interpretations (cho writeup)

Các diễn giải thêm so với chữ của đề, sẽ đưa vào mục "Interpretations" của writeup (cùng với R2, R3, R7, R8 theo Q23).

- Phần trăm trong evidence được cắt bớt, không làm tròn, để không bao giờ hiển thị 100%.
- (G2) Generator được rút lại một lựa chọn trong lúc sinh (tối đa 1.000 lần) khi lựa chọn đó vi phạm ràng buộc của chính generator; không bao giờ dựa trên kết quả rule của engine, và không lọc lại dataset sau khi sinh.

## Ràng buộc cho các task sau

**Task 12 (generator hợp lệ):**
- Dataset do generator sinh ra, khi đọc lại bằng `ClaimCsvReader`, phải cho **0 dòng bị loại và 0 cảnh báo**. Sẽ có test kiểm tra.
- Claim chỉ có P-0001 với chẩn đoán chỉ nội trú phải có trọng số rất thấp.
- Số lượng claim của 4 nhóm phương án 3 do generator quyết, nhưng: mỗi nhóm **ít nhất 30 claim**, và claim cài (clustering gian lận + R9 `legit_noise`) chiếm **không quá 25%** mỗi nhóm. Sẽ có test kiểm tra.

**Task 13 (upcoding, unbundling):**
- Upcoding: với mọi procedure có giá gốc ≥ 15.000, nếu số tiền rơi vào dải clustering thì lấy lại hệ số. Chỉ nhắm nhóm chữ ký có ít nhất 10 claim.
- Upcoding (Q1): claim upcoded phải là claim **một procedure**, vì rule bỏ qua claim nhiều procedure.
- Unbundling: nhiễu cắt ≤ 47.000.
- Duplicate: generator **chủ động tránh** đặt bản sao duplicate trong cửa sổ 7 ngày của một claim khác cùng member và chẩn đoán. Không lọc lại sau khi sinh. Số claim gian lận khớp từ 2 rule trở lên chỉ được **báo cáo** (Q4).

**Task 14 (clustering, weekend):**
- Claim clustering dùng P-2009 (phẫu thuật) phải đặt vào ngày thường, hoặc ở provider không thuộc nhóm "chỉ làm ngày thường", để không kích hoạt weekend anomaly.

**Task 17 (tuning):**
- Quyết định R4 (giữ R4-A hay đổi sang median/MAD, Q2) **chỉ** đưa ra trên dataset tuning (seed 42), **trước** khi đóng băng config. Dựa vào cả recall riêng của upcoding lẫn số liệu masking ở L9 (FP tự nhiên 34 xuống 9; CLM-01130 bị sót vì masking).

**Task 16 (metric):**
- Báo cáo `UpcodingRule.coverage(index)`: số claim được xét, bỏ qua vì nhiều procedure, vì nhóm < `n_min`, vì SD = 0.
- Báo cáo `UnbundlingRule.coverage(index)`: số claim được xét (ít nhất 2 mã khác nhau), bỏ qua vì chỉ có một procedure.
- Báo cáo `WeekendAnomalyRule.coverage(index)`: số claim cuối tuần có phẫu thuật được xét, và số bị bỏ qua vì lịch sử provider dưới `min_history`.
- Báo cáo `DiagnosisProcedureMismatchRule.coverage(index)`: số claim được xét, bỏ qua vì chẩn đoán không có trong whitelist, và số claim được xét có mã procedure không có trong catalog (mã đó bị bỏ qua).

## Ghi chú cho phiên sau

**Môi trường:**
- Shell reset thư mục về `C:\Users\Zen` sau mỗi lệnh, nên luôn dùng đường dẫn tuyệt đối.
- Git hệ thống đặt `core.autocrlf=true`; `.gitattributes` của repo ép LF.
- PowerShell 5.1 `Out-File -Encoding utf8` ghi BOM. Commit message nên truyền bằng `git commit -m ... -m ...`, không qua file tạo bằng PowerShell.
- Nhánh `main` đang trỏ upstream `origin/main`, nhưng remote chưa có nhánh này ("upstream is gone"). Hết khi push lần đầu bằng `git push -u origin main`. Chưa push lần nào.
- Hai file export ngày 25/10 đang ở ngoài repo, cần nhắc người dùng chuyển vào `docs/ai-log/`:
  - `C:\Users\Zen\2026-10-25-142613-cd-dworkingrepositoriespapaya-insurtech.txt`
  - `C:\Users\Zen\2026-10-25-154311-cd-dworkingrepositoriespapaya-insurtech.txt`

**Danh mục Task 02** (đã có file trong `src/main/resources/catalog/`, đây là tóm tắt lý do thiết kế):
- 21 chẩn đoán ICD-10 bản WHO, gồm 5 DENTAL, 7 OUTPATIENT, 7 INPATIENT, và 2 mã dùng cho cả hai loại (J18.9, M17.1).
- 40 procedure, trong đó 8 có cờ surgical (P-2006, P-2007, P-2009, P-2012, P-2013, P-2101, P-2102, P-3007).
  - Mã chung: P-0001 (300), P-0002 (200).
  - Đánh số theo nhóm: P-1xxx ngoại trú, P-2xxx nội trú, P-3xxx nha khoa.
- Whitelist: 64 cặp riêng, cộng 35 cặp mã chung.
- 6 bundle: P-1101 diabetes, P-1102 cardiovascular, P-1103 UTI, P-2101 appendectomy, P-2102 cholecystectomy, P-3101 root canal.
- Procedure dùng cho clustering: P-2009 caesarean (42.000) và P-2010 coronary angiography (40.000).
  - Không chọn 38.000 vì cần μ ≥ 38.461 để số tiền 49.999 vẫn có z ≤ 2 (CV 15%).
- Quy tắc giá nội trú: μ ≤ 32.000 hoặc μ ≥ 95.000. Như vậy chỉ hai procedure clustering tạo FP clustering tự nhiên.
- Tổ hợp hợp lệ gợi ý:
  - J18.9: P-2001 + P-2002.
  - I21.9: P-2010 + P-2011.
  - I63.9: P-2003 + P-2001.
  - J06.9: P-1001 + P-1002.
