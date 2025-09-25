package org.apache.hadoop.hbase.regionserver;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.shaded.protobuf.generated.RegionServerStatusProtos;
import org.apache.hadoop.hbase.util.Threads;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;
import org.apache.hadoop.fs.FileSystem;

/**
 * Subclass so can get at protected methods (none at moment). Also, creates a FileSystem instance
 * per instantiation. Adds a shutdown own FileSystem on the way out. Shuts down own Filesystem
 * only, not All filesystems as the FileSystem system exit hook does.
 */
public class MiniHBaseClusterRegionServer extends HRegionServer {
  private Thread shutdownThread = null;
  private User user = null;
  /**
   * List of RegionServers killed so far. ServerName also comprises startCode of a server, so any
   * restarted instances of the same server will have different ServerName and will not coincide
   * with past dead ones. So there's no need to cleanup this list.
   */
  public static Set<ServerName> killedServers = new HashSet<>();

  public MiniHBaseClusterRegionServer(Configuration conf)
    throws IOException, InterruptedException {
    super(conf);
    this.user = User.getCurrent();
  }

  /*
   * @param currentfs We return this if we did not make a new one.
   * @param uniqueName Same name used to help identify the created fs.
   * @return A new fs instance if we are up on DistributeFileSystem.
   */

  @Override
  protected void handleReportForDutyResponse(final RegionServerStatusProtos.RegionServerStartupResponse c)
    throws IOException {
    super.handleReportForDutyResponse(c);
    // Run this thread to shutdown our filesystem on way out.
    this.shutdownThread = new SingleFileSystemShutdownThread(getFileSystem());
  }

  @Override
  public void run() {
    try {
      this.user.runAs(new PrivilegedAction<Object>() {
        @Override
        public Object run() {
          runRegionServer();
          return null;
        }
      });
    } catch (Throwable t) {
      //LOG.error("Exception in run", t);
    } finally {
      // Run this on the way out.
      if (this.shutdownThread != null) {
        this.shutdownThread.start();
        Threads.shutdown(this.shutdownThread, 30000);
      }
    }
  }

  private void runRegionServer() {
    super.run();
  }

  @Override
  public void kill() {
    killedServers.add(getServerName());
    super.kill();
  }

  @Override
  public void abort(final String reason, final Throwable cause) {
    this.user.runAs(new PrivilegedAction<Object>() {
      @Override
      public Object run() {
        abortRegionServer(reason, cause);
        return null;
      }
    });
  }

  private void abortRegionServer(String reason, Throwable cause) {
    super.abort(reason, cause);
  }

  /**
   * Alternate shutdown hook. Just shuts down the passed fs, not all as default filesystem hook
   * does.
   */
  static class SingleFileSystemShutdownThread extends Thread {
    private final FileSystem fs;

    SingleFileSystemShutdownThread(final FileSystem fs) {
      super("Shutdown of " + fs);
      this.fs = fs;
    }

    @Override
    public void run() {
      try {
        //LOG.info("Hook closing fs=" + this.fs);
        this.fs.close();
      } catch (IOException e) {
        //LOG.warn("Running hook", e);
      }
    }
  }

}
