package store;

import agent.Message;

import java.util.List;

public interface SessionStore {

    void save(List<Message> messages) throws Exception;

    List<Message> load() throws Exception;

}
