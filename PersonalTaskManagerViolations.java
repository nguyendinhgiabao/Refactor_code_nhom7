import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Để tránh lỗi import, ta sẽ sử dụng ArrayList<Map<String, Object>> thay cho
 * JSONArray/JSONObject.
 * Dữ liệu sẽ được lưu/đọc dưới dạng JSON thủ công (chỉ mô phỏng, không dùng thư
 * viện ngoài).
 */
public class PersonalTaskManagerViolations {

    private static final String DB_FILE_PATH = "tasks_database.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] VALID_PRIORITIES = { "Thấp", "Trung bình", "Cao" };

    // Phương thức trợ giúp để tải dữ liệu (giả lập JSON, không dùng thư viện ngoài)
    private static List<Map<String, Object>> loadTasksFromDb() {
        List<Map<String, Object>> tasks = new ArrayList<>();
        File file = new File(DB_FILE_PATH);
        if (!file.exists()) {
            return tasks;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(DB_FILE_PATH))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String content = sb.toString().trim();
            if (content.isEmpty() || content.equals("[]")) {
                return tasks;
            }
            // Đơn giản hóa: mỗi dòng là 1 task dạng key1:value1;key2:value2;...
            String[] items = content.split("\\|\\|\\|");
            for (String item : items) {
                Map<String, Object> task = new HashMap<>();
                String[] pairs = item.split(";");
                for (String pair : pairs) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        task.put(kv[0], kv[1]);
                    }
                }
                tasks.add(task);
            }
        } catch (IOException e) {
            System.err.println("Lỗi khi đọc file database: " + e.getMessage());
        }
        return tasks;
    }

    // Phương thức trợ giúp để lưu dữ liệu
    private static void saveTasksToDb(List<Map<String, Object>> tasksData) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DB_FILE_PATH))) {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> task : tasksData) {
                List<String> pairs = new ArrayList<>();
                for (Map.Entry<String, Object> entry : task.entrySet()) {
                    pairs.add(entry.getKey() + ":" + entry.getValue());
                }
                sb.append(String.join(";", pairs)).append("|||");
            }
            writer.write(sb.toString());
        } catch (IOException e) {
            System.err.println("Lỗi khi ghi vào file database: " + e.getMessage());
        }
    }

    /**
     * Chức năng thêm nhiệm vụ mới
     */
    private boolean validateTask(String title, String dueDateStr, String priorityLevel) {
        if (title == null || title.trim().isEmpty()) {
            System.out.println("Lỗi: Tiêu đề không được để trống.");
            return false;
        }

        if (dueDateStr == null || dueDateStr.trim().isEmpty()) {
            System.out.println("Lỗi: Ngày đến hạn không được để trống.");
            return false;
        }

        try {
            LocalDate.parse(dueDateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            System.out.println("Lỗi: Ngày đến hạn không hợp lệ. Vui lòng sử dụng định dạng YYYY-MM-DD.");
            return false;
        }

        boolean isValidPriority = false;
        for (String validP : VALID_PRIORITIES) {
            if (validP.equals(priorityLevel)) {
                isValidPriority = true;
                break;
            }
        }

        if (!isValidPriority) {
            System.out.println("Lỗi: Mức độ ưu tiên không hợp lệ. Vui lòng chọn từ: Thấp, Trung bình, Cao.");
            return false;
        }

        return true;
    }

    /**
     * Phương thức kiểm tra trùng lặp task - Tách ra để tránh lặp code
     */
    private boolean isTaskDuplicate(List<Map<String, Object>> tasks, String title, String dueDateStr) {
        for (Map<String, Object> existingTask : tasks) {
            if (existingTask.get("title").toString().equalsIgnoreCase(title) &&
                    existingTask.get("due_date").toString().equals(dueDateStr)) {
                System.out.println(String.format("Lỗi: Nhiệm vụ '%s' đã tồn tại với cùng ngày đến hạn.", title));
                return true;
            }
        }
        return false;
    }

    /**
     * Chức năng thêm nhiệm vụ mới (đã được tối ưu để tránh DRY)
     */
    public Map<String, Object> addNewTaskWithViolations(String title, String description,
            String dueDateStr, String priorityLevel,
            boolean isRecurring) {

        // Sử dụng phương thức validateTask thay vì lặp lại code kiểm tra
        if (!validateTask(title, dueDateStr, priorityLevel)) {
            return null;
        }
        // Tải dữ liệu
        List<Map<String, Object>> tasks = loadTasksFromDb();

        // Sử dụng phương thức isTaskDuplicate thay vì lặp lại code kiểm tra
        if (isTaskDuplicate(tasks, title, dueDateStr)) {
            return null;
        }

        // Sửa KISS: thay vì dùng hàm generateSimpleId phức tạp hoặc UUID, dùng ID tăng
        // dần đơn giản
        String taskId = String.valueOf(tasks.size() + 1);
        LocalDate dueDate = LocalDate.parse(dueDateStr, DATE_FORMATTER);
        Map<String, Object> newTask = new HashMap<>();
        newTask.put("id", taskId);
        newTask.put("title", title);
        newTask.put("description", description);
        newTask.put("due_date", dueDate.format(DATE_FORMATTER));
        newTask.put("priority", priorityLevel);
        newTask.put("status", "Chưa hoàn thành");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        newTask.put("created_at", timestamp);
        newTask.put("last_updated_at", timestamp);

        // sửa YAGNI
        /*
         * newTask.put("is_recurring", isRecurring);
         * if (isRecurring) {
         * newTask.put("recurrence_pattern", "Chưa xác định");
         * }
         */

        tasks.add(newTask);

        // Lưu dữ liệu
        saveTasksToDb(tasks);

        System.out.println(String.format("Đã thêm nhiệm vụ mới thành công với ID: %s", taskId));
        return newTask;
    }

    public static void main(String[] args) {
        PersonalTaskManagerViolations manager = new PersonalTaskManagerViolations();
        System.out.println("\nThêm nhiệm vụ hợp lệ:");
        manager.addNewTaskWithViolations(
                "Mua sách",
                "Sách Công nghệ phần mềm.",
                "2025-07-20",
                "Cao",
                false);

        System.out.println("\nThêm nhiệm vụ trùng lặp (minh họa DRY - lặp lại code đọc/ghi DB và kiểm tra trùng):");
        manager.addNewTaskWithViolations(
                "Mua sách",
                "Sách Công nghệ phần mềm.",
                "2025-07-20",
                "Cao",
                false);

        System.out.println("\nThêm nhiệm vụ lặp lại (minh họa YAGNI - thêm tính năng không cần thiết ngay):");
        manager.addNewTaskWithViolations(
                "Tập thể dục",
                "Tập gym 1 tiếng.",
                "2025-07-21",
                "Trung bình",
                true);

        System.out.println("\nThêm nhiệm vụ với tiêu đề rỗng:");
        manager.addNewTaskWithViolations(
                "",
                "Nhiệm vụ không có tiêu đề.",
                "2025-07-22",
                "Thấp",
                false);
    }
}