package tool;

import java.time.LocalDateTime;

public class CurrentTimeTool implements Tool {

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String execute(String argument) throws Exception {
        return "当前系统时间：" + LocalDateTime.now();
    }

}
