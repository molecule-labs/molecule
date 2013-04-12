package molecule.examples.io.stopwatch

import molecule._
import molecule.io._
import molecule.channel.NativeProducer

import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Image
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowListener
import java.awt.event.WindowEvent
import java.net.URL
import java.util.Timer
import java.util.TimerTask

import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * @author Sebastien Bocq
 * @author Koen Daenen
 */
object SwingDisplay {

  val DISPLAY_PREFIX = "<html><font face=\"Courier\" color=\"maroon\"" +
    " size=\"10\"><b>&nbsp;&nbsp;"
  val DISPLAY_SUFFIX = "</b></font></html>"

  var watchImage = {
    val imageURL = this.getClass().getClassLoader().
      getResource("stopwatch/stopwatch.gif")
    createImage(imageURL)
  }
  var watchIcon = {
    val iconURL = this.getClass().getClassLoader().
      getResource("stopwatch/stopwatchicon.gif")
    createImage(iconURL)
  }

  def createImage(url: URL): Image =
    Toolkit.getDefaultToolkit().createImage(url)
}

class SwingDisplay(eventChannelCapacity: Int = 100) extends JFrame with WindowListener with ActionListener with io.Resource {

  import SwingDisplay._

  private val (eventIn, eventOut) = NativeProducer.mkOneToOne[Event](eventChannelCapacity)

  def eventCh = eventIn

  setupUI()

  var display: JLabel = _
  var start: JButton = _
  var split: JButton = _

  override def actionPerformed(e: ActionEvent) =
    e.getActionCommand() match {
      case "START" =>
        start.getText() match {
          case "Start" =>
            eventOut.send(Start)
            start.setText("Stop")
            split.setEnabled(true)
          case "Stop" =>
            eventOut.send(Stop)
            start.setText("Reset")
            split.setEnabled(false)
          case _ =>
            eventOut.send(Reset)
            start.setText("Start")
            split.setText("Split")
        }
      case "SPLIT" =>
        split.getText() match {
          case "Split" =>
            eventOut.send(Split)
            split.setText("Unsplit")
          case _ =>
            eventOut.send(Unsplit)
            split.setText("Split")
        }
    }

  def setupUI() {
    val panel = new WatchPanel()
    panel.setLayout(new BorderLayout())
    setContentPane(panel)
    display = new JLabel(DisplayTime().getDisplay())
    panel.add(display, BorderLayout.PAGE_START)
    start = makeButton("START", "start, stop, reset", "Start")
    panel.add(start, BorderLayout.LINE_START)
    split = makeButton("SPLIT", "split, unsplit", "Split")
    split.setEnabled(false)
    panel.add(split, BorderLayout.LINE_END)
    pack()
    setLocation(200, 200)
    setIconImage(watchIcon)
    setResizable(false)
    setSize(300, 125)
    setVisible(true)
    addWindowListener(this)
    //setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
  }

  val timeCh = SwingExecutor.newOChan { time: DisplayTime =>
    display.setText(DISPLAY_PREFIX + time.getDisplay() + DISPLAY_SUFFIX)
  }

  def makeButton(actionCommand: String, toolTipText: String, altText: String): JButton = {
    val button = new JButton(altText)
    button.setActionCommand(actionCommand)
    button.setToolTipText(toolTipText)
    button.addActionListener(this)
    button.setOpaque(false)
    button
  }

  def windowClosing(e: WindowEvent) = shutdown(EOS)

  def windowClosed(e: WindowEvent) {
  }

  def windowOpened(e: WindowEvent) {
  }

  def windowIconified(e: WindowEvent) {
  }

  def windowDeiconified(e: WindowEvent) {
  }

  def windowActivated(e: WindowEvent) {
  }

  def windowDeactivated(e: WindowEvent) {
  }

  class WatchPanel extends JPanel {

    override def paintComponent(g: Graphics) {
      if (watchImage != null) {
        g.drawImage(watchImage, 0, 0, this.getWidth(), this.getHeight(), this)
      }
    }
  }

  def shutdown(signal: Signal) = {
    dispose()
    eventOut.close(signal)
  }

  def poison(signal: Signal) = {
    shutdown(signal)
  }
}
