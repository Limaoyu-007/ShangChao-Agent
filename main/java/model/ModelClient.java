package model;

import agent.Message;

import java.util.List;

public interface ModelClient {

    Message chat(List<Message> messages) throws Exception;

}
