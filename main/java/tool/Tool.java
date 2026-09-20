package tool;

import java.util.Map;

public interface Tool {

    String name();

    String execute(String argument) throws Exception;

    String description();

    Map<String, Object> parameters();


}
