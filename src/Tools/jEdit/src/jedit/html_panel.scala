/*  Title:      Tools/jEdit/src/jedit/html_panel.scala
    Author:     Makarius

HTML panel based on Lobo/Cobra.
*/

package isabelle.jedit


import isabelle._

import java.io.StringReader
import java.awt.{BorderLayout, Dimension, GraphicsEnvironment, Toolkit, FontMetrics}
import java.awt.event.MouseEvent

import javax.swing.{JButton, JPanel, JScrollPane}
import java.util.logging.{Logger, Level}

import org.w3c.dom.html2.HTMLElement

import org.lobobrowser.html.parser.{DocumentBuilderImpl, InputSourceImpl}
import org.lobobrowser.html.gui.HtmlPanel
import org.lobobrowser.html.domimpl.{HTMLDocumentImpl, HTMLStyleElementImpl, NodeImpl}
import org.lobobrowser.html.test.{SimpleHtmlRendererContext, SimpleUserAgentContext}

import scala.io.Source
import scala.actors.Actor._


object HTML_Panel
{
  sealed abstract class Event { val element: HTMLElement; val mouse: MouseEvent }
  case class Context_Menu(val element: HTMLElement, mouse: MouseEvent) extends Event
  case class Mouse_Click(val element: HTMLElement, mouse: MouseEvent) extends Event
  case class Double_Click(val element: HTMLElement, mouse: MouseEvent) extends Event
  case class Mouse_Over(val element: HTMLElement, mouse: MouseEvent) extends Event
  case class Mouse_Out(val element: HTMLElement, mouse: MouseEvent) extends Event
}


class HTML_Panel(
  sys: Isabelle_System,
  initial_font_size: Int,
  handler: PartialFunction[HTML_Panel.Event, Unit]) extends HtmlPanel
{
  // global logging
  Logger.getLogger("org.lobobrowser").setLevel(Level.WARNING)


  /* pixel size -- cf. org.lobobrowser.html.style.HtmlValues.getFontSize */

  val screen_resolution =
    if (GraphicsEnvironment.isHeadless()) 72
    else Toolkit.getDefaultToolkit().getScreenResolution()

  def lobo_px(raw_px: Int): Int = raw_px * 96 / screen_resolution
  def raw_px(lobo_px: Int): Int = (lobo_px * screen_resolution + 95) / 96


  /* document template */

  private def try_file(name: String): String =
  {
    val file = sys.platform_file(name)
    if (file.isFile) Source.fromFile(file).mkString else ""
  }

  private def template(font_size: Int): String =
  {
    """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<style media="all" type="text/css">
""" +
  try_file("$ISABELLE_HOME/lib/html/isabelle.css") + "\n" +
  try_file("$ISABELLE_HOME_USER/etc/isabelle.css") + "\n" +
  "body { font-family: " + sys.font_family + "; font-size: " + raw_px(font_size) + "px }" +
"""
</style>
</head>
<body/>
</html>
"""
  }

  def font_metrics(font_size: Int): FontMetrics =
    Swing_Thread.now { getFontMetrics(sys.get_font(font_size)) }

  def panel_width(font_size: Int): Int =
    Swing_Thread.now {
      (getWidth() / (font_metrics(font_size).charWidth(Symbol.spc) max 1) - 4) max 20
    }


  /* actor with local state */

  private val ucontext = new SimpleUserAgentContext

  private val rcontext = new SimpleHtmlRendererContext(this, ucontext)
  {
    private def handle(event: HTML_Panel.Event): Boolean =
      if (handler != null && handler.isDefinedAt(event)) { handler(event); true }
      else false

    override def onContextMenu(elem: HTMLElement, event: MouseEvent): Boolean =
      handle(HTML_Panel.Context_Menu(elem, event))
    override def onMouseClick(elem: HTMLElement, event: MouseEvent): Boolean =
      handle(HTML_Panel.Mouse_Click(elem, event))
    override def onDoubleClick(elem: HTMLElement, event: MouseEvent): Boolean =
      handle(HTML_Panel.Double_Click(elem, event))
    override def onMouseOver(elem: HTMLElement, event: MouseEvent)
      { handle(HTML_Panel.Mouse_Over(elem, event)) }
    override def onMouseOut(elem: HTMLElement, event: MouseEvent)
      { handle(HTML_Panel.Mouse_Out(elem, event)) }
  }

  private val builder = new DocumentBuilderImpl(ucontext, rcontext)

  private case class Init(font_size: Int)
  private case class Render(body: List[XML.Tree])

  private val main_actor = actor {
    // crude double buffering
    var doc1: org.w3c.dom.Document = null
    var doc2: org.w3c.dom.Document = null

    var current_font_size = 16
    var current_font_metrics: FontMetrics = null

    def metric(s: String): Double =
      if (current_font_metrics == null) s.length.toDouble
      else current_font_metrics.stringWidth(s).toDouble / current_font_metrics.charWidth(Symbol.spc)

    loop {
      react {
        case Init(font_size) =>
          current_font_size = font_size
          current_font_metrics = font_metrics(lobo_px(raw_px(font_size)))

          val src = template(font_size)
          def parse() =
            builder.parse(new InputSourceImpl(new StringReader(src), "http://localhost"))
          doc1 = parse()
          doc2 = parse()
          Swing_Thread.now { setDocument(doc1, rcontext) }

        case Render(body) =>
          val doc = doc2
          val html_body =
            Pretty.formatted(body, panel_width(current_font_size), metric)
              .map(t => XML.elem(HTML.PRE, HTML.spans(t)))
          val node = XML.document_node(doc, XML.elem(HTML.BODY, html_body))
          doc.removeChild(doc.getLastChild())
          doc.appendChild(node)
          doc2 = doc1
          doc1 = doc
          Swing_Thread.now { setDocument(doc1, rcontext) }

        case bad => System.err.println("main_actor: ignoring bad message " + bad)
      }
    }
  }


  /* main method wrappers */

  def init(font_size: Int) { main_actor ! Init(font_size) }
  def render(body: List[XML.Tree]) { main_actor ! Render(body) }

  init(initial_font_size)
}
