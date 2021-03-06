package com.sksamuel.elastic4s

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.FieldType.StringType
import org.scalatest.{FreeSpec, Matchers}

class AnalyzerTest extends FreeSpec with Matchers with ElasticSugar {

  client.execute {
    create index "analyzer" mappings {
      "test" as(
        "keyword" typed StringType analyzer KeywordAnalyzer,
        "snowball" typed StringType analyzer SnowballAnalyzer,
        "whitespace" typed StringType analyzer WhitespaceAnalyzer,
        "stop" typed StringType analyzer StopAnalyzer,
        "standard1" typed StringType analyzer CustomAnalyzer("standard1"),
        "simple1" typed StringType analyzer SimpleAnalyzer,
        "pattern" typed StringType analyzer CustomAnalyzer("pattern1"),
        "ngram1" typed StringType analyzer CustomAnalyzer("ngram1")
        )
    } analysis (
      PatternAnalyzerDefinition("pattern1", "\\d", false),
      CustomAnalyzerDefinition("ngram1", NGramTokenizer),
      CustomAnalyzerDefinition("standard1", StandardTokenizer("stokenizer1", 10))
      )
  }.await

  client.execute {
    index into "analyzer/test" fields(
      "keyword" -> "light as a feather",
      "snowball" -> "flying in the skies",
      "whitespace" -> "and and and qwerty uiop",
      "standard1" -> "aaaaaaaaaaa",
      "simple" -> "LOWER-CASED",
      "ngram1" -> "starcraft",
      "stop" -> "and and and",
      "pattern" -> "abc123def"
      )
  }.await

  refresh("analyzer")
  blockUntilCount(1, "analyzer")

  "KeywordAnalyzer" - {
    "should index entire string as a single token" in {
      client.execute {
        search in "analyzer/test" query termQuery("keyword" -> "feather")
      }.await.getHits.getTotalHits shouldBe 0
    }
  }

  "NGramTokenizer" - {
    "should index n-combinations" in {
      client.execute {
        search in "analyzer/test" query termQuery("ngram1" -> "cr")
      }.await.getHits.getTotalHits shouldBe 1
      client.execute {
        search in "analyzer/test" query termQuery("ngram1" -> "craf")
      }.await.getHits.getTotalHits shouldBe 0
    }
  }

  "SnowballAnalyzer" - {
    "should stem words" in {
      client.execute {
        search in "analyzer/test" query termQuery("snowball" -> "sky")
      }.await.getHits.getTotalHits shouldBe 1
    }
  }


  "StandardAnalyzer" - {
    "should honour max token length" in {
      client.execute {
        search in "analyzer/test" query termQuery("standard1" -> "aaaaaaaaaaa")
      }.await.getHits.getTotalHits shouldBe 0
    }
  }

  "PatternAnalyzer" - {
    "should split on patterns" in {
      client.execute {
        search in "analyzer/test" query termQuery("pattern" -> "def")
      }.await.getHits.getTotalHits shouldBe 1
    }
  }

  "StopAnalyzer" - {
    "should exclude stop words" in {
      client.execute {
        search in "analyzer/test" query termQuery("stop" -> "and")
      }.await.getHits.getTotalHits shouldBe 0
    }
  }

  "SimpleAnalyzer" - {
    "should split on non-letter" in {
      client.execute {
        search in "analyzer/test" query termQuery("simple" -> "lower")
      }.await.getHits.getTotalHits shouldBe 1
    }
  }

  "WhitespaceAnalyzer" - {
    "should include stop words" in {
      client.execute {
        search in "analyzer/test" query termQuery("whitespace" -> "and")
      }.await.getHits.getTotalHits shouldBe 1
    }
    "should split on whitespace" in {
      client.execute {
        search in "analyzer/test" query termQuery("whitespace" -> "uiop")
      }.await.getHits.getTotalHits shouldBe 1
    }
  }
}
