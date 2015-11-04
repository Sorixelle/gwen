/*
 * Copyright 2014-2015 Branko Juric, Brady Wood
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gwen.eval

import java.io.File
import java.io.FileWriter
import scala.reflect.io.Path
import scala.util.{Failure => TryFailure}
import scala.util.{Success => TrySuccess}
import scala.util.Try
import org.mockito.Matchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.mock.MockitoSugar
import gwen.Predefs.Kestrel
import gwen.dsl.Scenario
import gwen.dsl.StatusKeyword
import gwen.dsl.Step
import gwen.dsl.StepKeyword
import org.scalatest.FlatSpec
import org.mockito.ArgumentCaptor
import gwen.dsl.Tag
import gwen.dsl.SpecType

class GwenInterpreterTest extends FlatSpec with Matchers with MockitoSugar {

  val rootDir = new File("target" + File.separator + "GwenInterpreterTest")
  Path(rootDir).createDirectory()
  
  val options = new GwenOptions();
  
  private def interpreter(mockEnv: EnvContext) = {
    trait MockEvalEngine extends EvalEngine[EnvContext] {
      type EnvContextType = EnvContext
      override private [eval] def init(options: GwenOptions, scopes: ScopedDataStack): EnvContextType = mockEnv
      override def evaluate(step: Step, env: EnvContextType) { }
    }
    new GwenInterpreter[EnvContext] with MockEvalEngine
  }
  
  private def executor(mockInterpreter: GwenInterpreter[EnvContext], mockEnv: EnvContext) = {
    new GwenLauncher(mockInterpreter)
  }
  
  "initialise interpreter" should "create new env" in {
    val mockEnv = mock[EnvContext]
    interpreter(mockEnv).initialise(options)
    verify(mockEnv, never()).close()
  }
  
  "closing interpreter" should "close env" in {
    val mockEnv = mock[EnvContext]
    interpreter(mockEnv).close(mockEnv)
    verify(mockEnv).close()
  }
  
  "resetting interpreter" should "reset env" in {
    val mockEnv = mock[EnvContext]
    interpreter(mockEnv).reset(mockEnv)
    verify(mockEnv).reset()
  }
  
  "interpreting a valid step" should "return success" in {
    val mockEnv = mock[EnvContext]
    when(mockEnv.getStepDef("I am a valid step")).thenReturn(None)
    val step = Step(StepKeyword.Given, "I am a valid step")
    when(mockEnv.interpolate(step)).thenReturn(step)
    val result = interpreter(mockEnv).interpretStep("Given I am a valid step", mockEnv)
    result match {
      case TrySuccess(step) =>
        step.keyword should be (StepKeyword.Given)
        step.expression should be ("I am a valid step")
        step.evalStatus.status should be (StatusKeyword.Passed)
      case TryFailure(err) =>
        fail(s"success expected but got ${err}")
    }
  }
  
  "interperting a valid step def" should "return success" in {
    val localScope = new LocalDataStack()
    val mockEnv = mock[EnvContext]
    val step1 = Step(StepKeyword.Given, "I am a step in the stepdef")
    val step2 = Step(StepKeyword.Given, "I am a valid stepdef")
    val stepdef = Scenario(Set[Tag](Tag.StepDefTag), "I am a valid stepdef", None, List(step1))
    when(mockEnv.getStepDef("I am a valid stepdef")).thenReturn(Some((stepdef, Nil)))
    when(mockEnv.getStepDef("I am a step in the stepdef")).thenReturn(None)
    when(mockEnv.attachments).thenReturn(Nil)
    when(mockEnv.interpolate(step1)).thenReturn(step1)
    when(mockEnv.interpolate(step2)).thenReturn(step2)
    when(mockEnv.localScope).thenReturn(localScope)
    val result = interpreter(mockEnv).interpretStep("Given I am a valid stepdef", mockEnv)
    result match {
      case TrySuccess(step) =>
        step.keyword should be (StepKeyword.Given)
        step.expression should be ("I am a valid stepdef")
        step.evalStatus.status should be (StatusKeyword.Passed)
      case TryFailure(err) => 
        fail(s"success expected but got ${err}")
    }
  }
  
  "interpreting an invalid step" should "return error" in {
    val mockEnv = mock[EnvContext]
    val result = interpreter(mockEnv).interpretStep("Yes I am an invalid step", mockEnv)
    result match {
      case TrySuccess(step) =>
        fail("expected failure")
      case TryFailure(err) =>
        err.getMessage() should be ("""[1.4] failure: 'Given|When|Then|And|But' expected
          |
          |Yes I am an invalid step
          |   ^""".stripMargin.replace("\r", ""))
    }
  }
  
  "interpreting valid feature file with no meta" should "return success" in {
    val featureString = """
     Feature: Gwen
  Background: The observer
        Given I am an observer
    Scenario: The butterfly effect
        Given a deterministic nonlinear system
         When a small change is initially applied
         Then a large change will eventually result"""
    
    val featureFile = writeToFile(featureString, createFile("test1.feature"))
    val mockEnv = mock[EnvContext]
    when(mockEnv.specType).thenReturn(SpecType.feature) 
    when(mockEnv.getStepDef(anyString)).thenReturn(None)
    val step1 = Step(StepKeyword.Given, "I am an observer")
    val step2 = Step(StepKeyword.Given, "a deterministic nonlinear system")
    val step3 = Step(StepKeyword.When, "a small change is initially applied")
    val step4 = Step(StepKeyword.Then, "a large change will eventually result")
    when(mockEnv.interpolate(step1)).thenReturn(step1)
    when(mockEnv.interpolate(step2)).thenReturn(step2)
    when(mockEnv.interpolate(step3)).thenReturn(step3)
    when(mockEnv.interpolate(step4)).thenReturn(step4)
    val result = interpreter(mockEnv).interpretFeature(featureFile, Nil, None, Nil, mockEnv)
    result match {
      case feature::_ =>
        feature.evalStatus.status should be (StatusKeyword.Passed)
      case Nil => 
        fail("List(feature) expected")
    }
  }
  
  "interpreting valid feature file with meta having stepdef" should "return success" in {
    val metaString = """
     Feature: Gwen meta
     @Stepdef
    Scenario: the butterfly flaps its wings
        Given a deterministic nonlinear system
         When a small change is initially applied
         Then a large change will eventually result"""
    val featureString = """
     Feature: Gwen
  Background: The observer
        Given I am an observer
    Scenario: The butterfly effect
        Given the butterfly flaps its wings"""
    
    val localScope = new LocalDataStack()
    val metaFile = writeToFile(metaString, createFile("test2.meta"))
    val featureFile = writeToFile(featureString, createFile("test2.feature"))
    val stepdef = Scenario(
      Set[Tag](),
      "the butterfly flaps its wings", 
      None, 
      List(
        Step(StepKeyword.Given, "a deterministic nonlinear system"),
        Step(StepKeyword.When, "a small change is initially applied"),
        Step(StepKeyword.Then, "a large change will eventually result")))
    val mockEnv = mock[EnvContext]
    when(mockEnv.specType).thenReturn(SpecType.feature)
    when(mockEnv.getStepDef("I am an observer")).thenReturn(None)
    when(mockEnv.getStepDef("the butterfly flaps its wings")).thenReturn(Some((stepdef, Nil)))
    when(mockEnv.getStepDef("a deterministic nonlinear system")).thenReturn(None)
    when(mockEnv.getStepDef("a small change is initially applied")).thenReturn(None)
    when(mockEnv.getStepDef("a large change will eventually result")).thenReturn(None)
    when(mockEnv.getStepDef("")).thenReturn(None)
    when(mockEnv.attachments).thenReturn(Nil)
    when(mockEnv.localScope).thenReturn(localScope)
    val step1 = Step(StepKeyword.Given, "I am an observer")
    val step2 = Step(StepKeyword.Given, "the butterfly flaps its wings")
    val step3 = Step(StepKeyword.Given, "a deterministic nonlinear system")
    val step4 = Step(StepKeyword.When, "a small change is initially applied")
    val step5 = Step(StepKeyword.Then, "a large change will eventually result")
    when(mockEnv.interpolate(step1)).thenReturn(step1)
    when(mockEnv.interpolate(step2)).thenReturn(step2)
    when(mockEnv.interpolate(step3)).thenReturn(step3)
    when(mockEnv.interpolate(step4)).thenReturn(step4)
    when(mockEnv.interpolate(step5)).thenReturn(step5)
    val result = interpreter(mockEnv).interpretFeature(featureFile, List(metaFile), None, Nil, mockEnv)
    result match {
      case feature::_ =>
        feature.evalStatus.status should be (StatusKeyword.Passed)
      case Nil => 
        fail("List(feature) expected")
    }
  }
  
  private def createDir(dirname: String): File = {
    val dir = new File(rootDir, dirname)
    val path = Path(dir)
    path.deleteRecursively()
    path.createDirectory()
    dir
  }
  
  private def createFile(filepath: String): File = {
    val file = new File(rootDir + File.separator + filepath.replace('/', File.separatorChar))
    val path = Path(file)
    if (path.exists) {
      path.delete()
    }
    path.createFile(true)
    file
  }
  
  private def writeToFile(content: String, targetFile: File): File = 
    targetFile tap { file =>
      new FileWriter(file) tap { fw =>
        try {
          fw.write(content)
        } finally {
          fw.close
        }
      }
    }
  
}