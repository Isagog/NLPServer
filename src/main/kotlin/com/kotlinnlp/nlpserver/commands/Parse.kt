/* Copyright 2016-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.nlpserver.commands

import com.beust.klaxon.json
import com.kotlinnlp.conllio.Sentence as CoNLLSentence
import com.kotlinnlp.conllio.Token as CoNLLToken
import com.kotlinnlp.languagedetector.LanguageDetector
import com.kotlinnlp.linguisticdescription.POSTag
import com.kotlinnlp.linguisticdescription.language.Language
import com.kotlinnlp.linguisticdescription.sentence.MorphoSynSentence
import com.kotlinnlp.linguisticdescription.sentence.token.MorphoSynToken
import com.kotlinnlp.linguisticdescription.sentence.token.RealToken
import com.kotlinnlp.neuralparser.NeuralParser
import com.kotlinnlp.neuralparser.helpers.preprocessors.BasePreprocessor
import com.kotlinnlp.neuralparser.helpers.preprocessors.MorphoPreprocessor
import com.kotlinnlp.neuralparser.helpers.preprocessors.SentencePreprocessor
import com.kotlinnlp.neuralparser.language.BaseSentence
import com.kotlinnlp.neuralparser.language.BaseToken
import com.kotlinnlp.neuraltokenizer.NeuralTokenizer
import com.kotlinnlp.neuraltokenizer.Sentence
import com.kotlinnlp.neuraltokenizer.Token
import com.kotlinnlp.nlpserver.LanguageNotSupported
import com.kotlinnlp.nlpserver.commands.utils.TokenizingCommand
import spark.Response

/**
 * The command executed on the route '/parse'.
 *
 * @param languageDetector a language detector (can be null)
 * @param tokenizers a map of languages ISO 639-1 codes to neural tokenizers
 * @param parsers a map of languages ISO 639-1 codes to the related [NeuralParser]s
 * @param morphoPreprocessors a map of languages ISO 639-1 codes to morpho-preprocessors
 */
class Parse(
  override val languageDetector: LanguageDetector?,
  override val tokenizers: Map<String, NeuralTokenizer>,
  private val parsers: Map<String, NeuralParser<*>>,
  private val morphoPreprocessors: Map<String, MorphoPreprocessor>
) : TokenizingCommand {

  /**
   * The format of the parsing response.
   *
   * @property CoNLL the response will be written in CoNLL format
   * @property JSON the response will be written in JSON format
   */
  enum class ResponseFormat { CoNLL, JSON }

  /**
   * A base sentence preprocessor.
   */
  private val basePreprocessor = BasePreprocessor()

  /**
   * Parse the given [text], eventually forcing on the language [lang].
   *
   * @param text the text to parse
   * @param lang the language to use to parse the [text] (default = unknown)
   * @param format the string format of the parsed sentences response (default = JSON)
   * @param response the response of the server
   * @param prettyPrint pretty print, used for JSON format (default = false)
   *
   * @return the parsed [text] in the given string [format]
   */
  operator fun invoke(text: String,
                      lang: Language?,
                      format: ResponseFormat,
                      response: Response,
                      prettyPrint: Boolean): String {

    this.checkText(text)

    val textLanguage: Language = this.getTextLanguage(text = text, forcedLang = lang)
    val sentences: List<Sentence> = this.tokenizers.getValue(textLanguage.isoCode).tokenize(text)
    val parser: NeuralParser<*> = this.parsers[textLanguage.isoCode] ?: throw LanguageNotSupported(textLanguage.isoCode)
    val preprocessor: SentencePreprocessor = this.morphoPreprocessors[textLanguage.isoCode] ?: basePreprocessor

    if (format == ResponseFormat.CoNLL) response.header("Content-Type", "text/plain")

    return when (format) {
      ResponseFormat.CoNLL -> this.parseToCoNLLFormat(
        parser = parser,
        sentences = sentences,
        preprocessor = preprocessor)
      ResponseFormat.JSON -> this.parseToJSONFormat(
        parser = parser,
        sentences = sentences,
        preprocessor = preprocessor,
        lang = textLanguage,
        prettyPrint = prettyPrint)
    }
  }

  /**
   * Parse the given [sentences] and return the response in CoNLL format.
   *
   * @param parser the parser to use
   * @param sentences the list of sentences to parse
   * @param preprocessor a sentence preprocessor
   *
   * @return the parsed sentences in CoNLL string format
   */
  private fun parseToCoNLLFormat(parser: NeuralParser<*>,
                                 preprocessor: SentencePreprocessor,
                                 sentences: List<Sentence>): String =
    sentences.joinToString(separator = "\n\n", postfix = "\n") {
      parser
        .parse(preprocessor.convert(it.toBaseSentence()))
        .toCoNLL()
        .toCoNLLString(writeComments = false)
    }

  /**
   * Parse the given [sentences] and return the response in JSON format.
   *
   * @param parser the parser to use
   * @param sentences the list of sentences to parse
   * @param preprocessor a sentence preprocessor
   * @param lang the text language
   * @param prettyPrint pretty print (default = false)
   *
   * @return the parsed sentences in JSON string format
   */
  private fun parseToJSONFormat(parser: NeuralParser<*>,
                                sentences: List<Sentence>,
                                preprocessor: SentencePreprocessor,
                                lang: Language,
                                prettyPrint: Boolean = false): String = json {
    obj(
      "lang" to lang.isoCode,
      "sentences" to array(sentences.map {
        parser.parse(preprocessor.convert(it.toBaseSentence())).toJSON()
      })
    )
  }.toJsonString(prettyPrint = prettyPrint) + if (prettyPrint) "\n" else ""

  /**
   * @return a new base sentence built from this tokenizer sentence
   */
  private fun Sentence.toBaseSentence() = BaseSentence(
    id = this.position.index, // the index is unique within the list of tokenized sentences
    tokens = this.tokens.mapIndexed { i, it -> it.toBaseToken(id = i) },
    position = this.position
  )

  /**
   * @param id the token ID
   *
   * @return a new base token built from this tokenizer token
   */
  private fun Token.toBaseToken(id: Int) = BaseToken(id = id, position = this.position, form = this.form)

  /**
   * Convert this [MorphoSynSentence] to a CoNLL Sentence.
   *
   * @return a CoNLL Sentence
   */
  private fun MorphoSynSentence.toCoNLL(): CoNLLSentence = CoNLLSentence(
    sentenceId = this.id.toString(),
    text = this.buildText(),
    tokens = this.tokens.mapIndexed { i, it ->
      it.toCoNLL(
        id = i + 1,
        headId = it.syntacticRelation.governor?.let { id -> this.tokens.indexOfFirst { it.id == id } + 1 } ?: 0)
    }
  )

  /**
   * @return the CoNLL object that represents this token
   */
  private fun MorphoSynToken.toCoNLL(id: Int, headId: Int) = CoNLLToken(
    id = id,
    form = (this as? RealToken)?.form ?: CoNLLToken.EMPTY_FILLER,
    lemma = CoNLLToken.EMPTY_FILLER,
    posList = this.flatPOS.let { if (it.isNotEmpty()) it else listOf(POSTag(CoNLLToken.EMPTY_FILLER)) },
    pos2List = listOf(POSTag(CoNLLToken.EMPTY_FILLER)),
    feats = emptyMap(),
    head = headId,
    syntacticDependencies = this.flatSyntacticRelations.map { it.dependency },
    multiWord = null
  )
}
