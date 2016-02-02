package ammonite.sshd

import acyclic.file
import java.io.{InputStream, OutputStream, PrintStream}

import ammonite.ops.Path
import ammonite.sshd.util.Environment
import ammonite.repl.{Bind, Ref, Repl, Storage}

import scala.language.postfixOps

/**
 * An ssh server which serves ammonite repl as it's shell channel.
 * To start listening for incoming connections call
 * [[start()]] method. You can [[stop()]] the server at any moment.
 * It will also close all running sessions
 * @param sshConfig configuration of ssh server,
 *                  such as users credentials or port to be listening to
 * @param predef predef that will be installed on repl instances served by this server
 * @param replArgs arguments to pass to ammonite repl on initialization of the session
 */
class SshdRepl(sshConfig: SshServerConfig,
               predef: String = "",
               replArgs: Seq[Bind[_]] = Nil) {
  private lazy val sshd = SshServer(sshConfig, shellServer =
    SshdRepl.runRepl(sshConfig.ammoniteHome, predef, replArgs))

  def port = sshd.getPort
  def start(): Unit = sshd.start()
  def stop(): Unit = sshd.stop()
  def stopImmediately(): Unit = sshd.stop(true)
}


object SshdRepl {
  private def replServerClassLoader = SshdRepl.getClass.getClassLoader

  // Actually runs a repl inside of session serving a remote user shell.
  private def runRepl(homePath: Path, predef: String, replArgs: Seq[Bind[_]])
                     (in: InputStream, out: OutputStream): Unit = {
    // since sshd server has it's own customised environment,
    // where things like System.out will output to the
    // server's console, we need to prepare individual environment
    // to serve this particular user's session
    val replSessionEnv = Environment(replServerClassLoader, in, out)
    Environment.withEnvironment(replSessionEnv) {
      try {
        new Repl(in, out, out, Ref(Storage(homePath, None)), predef, replArgs).run()
      } catch {
        case any: Throwable =>
          val sshClientOutput = new PrintStream(out)
          sshClientOutput.println("What a terrible failure, the REPL just blow up!")
          any.printStackTrace(sshClientOutput)
      }
    }
  }
}