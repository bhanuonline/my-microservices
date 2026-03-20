#!/usr/bin/env node

import OpenAI from "openai";

const client = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

const prompt = process.argv.slice(2).join(" ");

if (!prompt) {
  console.log("Usage: gpt \"Your question here\"");
  process.exit(1);
}

const response = await client.responses.create({
  model: "gpt-4.1-mini",
  input: prompt,
});

console.log(response.output[0].content[0].text);
