package io.switchlab.app.switching;

import io.switchlab.sw.q2.SwitchRuntime;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class SwitchLifecycle implements SmartLifecycle {
  private final ListenerRepository listeners;
  private final SwitchProperties props;
  private SwitchRuntime runtime;

  public SwitchLifecycle(ListenerRepository listeners, SwitchProperties props) {
    this.listeners = listeners;
    this.props = props;
  }

  @Override
  public synchronized void start() {
    runtime = new SwitchRuntime(props.deployDir(), listeners.enabled());
    runtime.start(props.startupTimeout());
  }

  @Override
  public synchronized void stop() {
    if (runtime != null) {
      runtime.stop();
      runtime = null;
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return runtime != null;
  }
}
