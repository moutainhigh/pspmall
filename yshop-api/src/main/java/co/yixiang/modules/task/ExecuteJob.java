package co.yixiang.modules.task;

import java.util.Map;

/**
 * Created by kl on 2018/7/20.
 * Content :延时job执行器接口
 */
public interface ExecuteJob {

    void execute(Map<String,Object> job);
}
