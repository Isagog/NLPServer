/* Copyright 2020-present Simone Cangialosi. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package api

import com.kotlinnlp.ApiClient
import com.kotlinnlp.api.NlpServiceApi
import com.kotlinnlp.api.model.InputText
import com.kotlinnlp.api.model.TokenizedSentence
import com.xenomachina.argparser.mainBody

/**
 * Test the `tokenize` method of the KotlinNLP APIs.
 *
 * Launch with the '-h' option for help about the command line arguments.
 */
fun main(args: Array<String>) = mainBody {

  val parsedArgs = CommandLineArguments(args)
  val client = NlpServiceApi(ApiClient().setBasePath("http://${parsedArgs.host}:${parsedArgs.port}"))

  inputLoop {

    val sentences: List<TokenizedSentence> = client.tokenize(InputText().text(it), false)

    println("\nSentences:")
    sentences.forEach { s ->
      println("[${s.start}-${s.end}] |${s.tokens.joinToString("|") { t -> t.form }}|")
    }
  }
}
