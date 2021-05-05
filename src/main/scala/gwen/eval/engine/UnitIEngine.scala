/*
 * Copyright 2021 Branko Juric, Brady Wood
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

package gwen.eval.engine

import gwen.Errors
import gwen.eval.EvalContext
import gwen.eval.EvalEngine
import gwen.eval.SpecNormaliser
import gwen.model._
import gwen.model.gherkin._

import scala.util.Success
import scala.util.Failure

import com.typesafe.scalalogging.LazyLogging
import io.cucumber.gherkin.ParserException

import java.io.File

/**
  * Feature unit evaluation engine
  */
trait UnitEngine[T <: EvalContext]
  extends SpecEngine[T] with BackgroundEngine[T] with RuleEngine[T] with ScenarioEngine[T] with ExamplesEngine[T] 
  with StepDefEngine[T] with StepEngine[T] with GherkinParser with SpecNormaliser with LazyLogging {
    engine: EvalEngine[T] =>
  
  /**
    * Interprets a feature unit.
    *
    * @param unit the feature unit to process
    * @param ctx the evaluation context
    */
  def evaluateUnit(unit: FeatureUnit, ctx: T): Option[SpecResult] = {
    evaluateUnit(unit, Nil, ctx)
  }

  /**
    * Interprets a feature unit. Recursively loads all meta first followed by feature.
    *
    * @param unit the feature unit to process
    * @param loadedMeta cumulative meta
    * @param ctx the evaluation context
    */
  private def evaluateUnit(unit: FeatureUnit, loadedMeta: List[SpecResult], ctx: T): Option[SpecResult] = {
    Option(unit.featureFile).filter(_.exists()) map { file =>
      parseSpec(file) match {
        case Success(pspec) =>
          unit.tagFilter.filter(pspec) map { spec =>
            ctx.withEnv { env =>
              unit.dataRecord foreach { rec =>
                env.topScope.set("data record number", rec.recordNo.toString)
              }
              val metaResults = loadMeta(unit, spec, loadedMeta, ctx)
              if (spec.isMeta) {
                evaluateMeta(unit, spec, metaResults, unit.dataRecord, ctx)
              } else {
                ctx.lifecycle.beforeUnit(unit, env.scopes)
                evaluateFeature(unit, spec, metaResults, unit.dataRecord, ctx) tap { result => 
                  ctx.lifecycle.afterUnit(FeatureUnit(unit.ancestor, unit, result), env.scopes)
                }
              }
            }
          }
        case Failure(e) =>
          e match {
            case pe: ParserException => Errors.syntaxError(unit.uri, pe)
            case _ => Errors.syntaxError(e.getMessage)
          }
      }
    } getOrElse {
      logger.warn(s"Skipped missing feature file: ${unit.featureFile.getPath}") 
      None
    }
  }

  private def loadMeta(unit: FeatureUnit, meta: Spec, loadedMeta: List[SpecResult], ctx: T): List[SpecResult] = {
    val metaFiles = unit.metaFiles ++ findMetaImportFiles(meta, unit.featureFile)
    metaFiles.foldLeft(loadedMeta) { (loaded, file) =>
      if (!loadedMeta.flatMap(_.spec.specFile).contains(file)) {
        val metaUnit = FeatureUnit(unit, file, Nil, None, unit.tagFilter)
        loaded ++ evaluateUnit(metaUnit, loaded, ctx).toList
      } else {
        loaded
      }
    }
  }

  private def findMetaImportFiles(spec: Spec, specFile: File): List[File] = {
    spec.feature.tags.flatMap { tag =>
      tag match {
        case Tag(_, name, Some(filepath)) =>
          if (name == ReservedTags.Import.toString) {
            val file = new File(filepath)
            if (!file.exists()) Errors.missingOrInvalidImportFileError(tag)
            if (!file.getName.endsWith(".meta")) Errors.unsupportedImportError(tag)
            if (file.getCanonicalPath.equals(specFile.getCanonicalPath)) {
              Errors.recursiveImportError(tag)
            }
            Some(file)
          } else if (name.equalsIgnoreCase(ReservedTags.Import.toString)) {
            Errors.invalidTagError(s"""Invalid import syntax: $tag - correct syntax is @Import("filepath")""")
          } else {
            None
          }
        case _ => None
      }
    }
  }
  
}
