#!/bin/env node

import { readdir, readFile } from "node:fs/promises";
import { styleText } from "node:util";
import xml2js from "xml2js";

const resDirectoryUrl = new URL("./app/src/main/res/", import.meta.url);
const baseStringsUrl = new URL("./values/strings.xml", resDirectoryUrl);

const baseStrings = await parseStringsFile(baseStringsUrl);
const resDirectoryEntries = await readdir(resDirectoryUrl, {
    withFileTypes: true,
});

const filteredLanguageCodes = process.argv.slice(2).flatMap((arg) => arg.split(","));

/** @typedef {(name: string, value: string) => void } CheckerFunction */

// TODO: https://github.com/streetcomplete/StreetComplete/discussions/4059

/** @type {Record<string, CheckerFunction>} */
const languageChecks = {
    "*": (name, value, languageCode) => {
        checkStartEndDoubleQuotes(name, value);
        checkStartEndWhitespace(name, value);
        checkEllipsisCharacter(name, value);
        checkApostropheCharacter(name, value, languageCode);
    },
    de: (name, value) => {},
    en: (name, value) => {
        checkUsDoubleQuoteCharacter(name, value);
        checkNoSpaceBeforeEllipsis(name, value);
    },
    "en-rGB": (name, value) => {
        const expectedValue = baseStrings[name]
            .replaceAll("an apartment building", "a block of flats")
            .replaceAll("airplanes", "aeroplanes")
            .replaceAll("analyze", "analyse")
            .replaceAll("Apartment building", "Flats")
            .replaceAll("Artificial turf", "Astroturf")
            .replaceAll("ATM", "cash machine")
            .replaceAll("authorized", "authorised")
            .replaceAll("authorization", "authorisation")
            .replaceAll("Authorization", "Authorisation")
            .replaceAll("authorize", "authorise")
            .replaceAll("Authorize", "Authorise")
            .replaceAll("baby changing table", "baby-changing table")
            .replaceAll("bicycle lane", "cycle lane")
            .replaceAll("bicycle path", "cycle path")
            .replaceAll("Bicycle store", "Bicycle shop")
            .replaceAll("bike path", "cycleway")
            .replaceAll("Bike path", "Cycleway")
            .replaceAll("center", "centre")
            .replaceAll("Center", "Centre")
            .replaceAll("color", "colour")
            .replaceAll("collection box", "postbox")
            .replaceAll("crosswalk", "pedestrian crossing")
            .replaceAll("curb", "kerb")
            .replaceAll("Curb", "Kerb")
            .replaceAll("Dormitory building", "Halls of residence")
            .replaceAll("foot path", "footpath")
            .replaceAll("footway", "footpath")
            .replaceAll("Footway", "Footpath")
            .replaceAll("Garden allotment outbuilding", "Allotment building")
            .replaceAll("gas station", "fuel station")
            .replaceAll("identification number", "reference number")
            .replaceAll("Kindergarten", "Nursery school")
            .replaceAll("License", "Licence")
            .replaceAll("living street", "Home Zone")
            .replaceAll("mail carrier", "postman")
            .replaceAll("Mobile home", "Static caravan")
            .replaceAll("neighbor", "neighbour")
            .replaceAll("one way", "one-way")
            .replaceAll("oneway", "one-way")
            .replaceAll("Oneway", "One-way")
            .replaceAll("Optimized", "Optimised")
            .replaceAll("organization", "organisation")
            .replaceAll("pickup times", "collection times")
            .replaceAll("practiced", "practised")
            .replaceAll("restaurants, cafés, stores…", "restaurants, cafés, supermarkets, shops…")
            .replaceAll("restroom", "toilet")
            .replaceAll("Restroom", "Toilet")
            .replaceAll("roadway", "carriageway")
            .replaceAll("Routing engine", "Routeing engine")
            .replaceAll("spelled", "spelt")
            .replaceAll("sidewalk", "pavement")
            .replaceAll("Sidewalk", "Pavement")
            .replaceAll("Soccer", "Football")
            .replaceAll("Storefront entrance", "Shop entrance")
            .replaceAll("takeout", "takeaway")
            .replaceAll("travelers", "travellers")
            .replaceAll("travel trailers", "caravans")
            .replaceAll("walk signal", "green man")
            .replaceAll("waste basket", "bin");

        if (value !== expectedValue) {
            console.log(`${name}:`);
            if (expectedValue !== baseStrings[name]) {
                console.log(styleText("grey", `· "${baseStrings[name]}"`));
            }
            console.log(styleText("green", `- "${expectedValue}"`));
            console.log(styleText("red", `+ "${value}"`));
            console.log();
        }
    },
};

const prefix = "values-";
for (const entry of resDirectoryEntries) {
    if (!entry.isDirectory() || !entry.name.startsWith(prefix)) {
        continue;
    }

    const languageCode = entry.name.slice(prefix.length);

    if (filteredLanguageCodes.length > 0 && !filteredLanguageCodes.includes(languageCode)) {
        continue;
    }

    console.log(styleText(["blueBright", "bold"], `Checking "${languageCode}":`));

    const stringsUrl = new URL(`./${entry.name}/strings.xml`, resDirectoryUrl);
    const strings = await parseStringsFile(stringsUrl);
    if (!strings) {
        console.log(styleText("grey", `"${stringsUrl.pathname}" not found.\n`));
        continue;
    }

    for (const [name, value] of Object.entries(strings)) {
        languageChecks["*"](name, value, languageCode);

        if (languageCode in languageChecks) {
            languageChecks[languageCode](name, value);
        }
    }
    console.log(styleText("grey", `Check completed.\n`));
}

async function parseStringsFile(url) {
    try {
        const content = await readFile(url, "utf8");

        const parser = new xml2js.Parser({});
        const xml = await parser.parseStringPromise(content);

        return Object.fromEntries(
            xml.resources.string.map((string) => {
                const name = string.$.name;
                let value = string._.replaceAll("\\n", "\n");

                if (value.startsWith('"') && value.endsWith('"')) {
                    value = value.slice(1, -1).replaceAll('\\"', '"');
                }

                return [name, value];
            })
        );
    } catch (error) {
        if (error.code === "ENOENT") {
            return undefined;
        }
        throw error;
    }
}

/** @type {CheckerFunction} */
function checkStartEndDoubleQuotes(name, value) {
    const quoteCount = value.split("").filter((char) => char === '"').length;
    if (quoteCount % 2 === 0) {
        return;
    }

    if (value.startsWith('"') && value.endsWith('"')) {
        console.log(`${name}:`);
        console.log(styleText("red", `shouldn't start with quote character`));
        console.log();
    }
    if (value.endsWith('"') && !value.startsWith('"')) {
        console.log(`${name}:`);
        console.log(styleText("red", `shouldn't end with quote character`));
        console.log();
    }
}

/** @type {CheckerFunction} */
function checkStartEndWhitespace(name, value) {
    if (/^\s/.test(value)) {
        console.log(`${name}:`);
        console.log(styleText("red", `shouldn't start with whitespace character`));
        console.log();
    }
    if (/\s$/.test(value)) {
        console.log(`${name}:`);
        console.log(styleText("red", `shouldn't end with whitespace character`));
        console.log();
    }
}

/** @type {CheckerFunction} */
function checkEllipsisCharacter(name, value) {
    if (value.includes("...")) {
        console.log(`${name}:`);
        console.log(styleText("red", `ellipsis (...) should be replaced with … character`));
        console.log();
    }
}

/** @type {CheckerFunction} */
function checkApostropheCharacter(name, value) {
    if (value.includes("'")) {
        console.log(`${name}:`);
        console.log(
            styleText(
                "red",
                `apostrophe (') should be replaced with ’ character (single quotation marks should be replaced with ‘’ characters)`
            )
        );
        console.log();
    }
}

/** @type {CheckerFunction} */
function checkUsDoubleQuoteCharacter(name, value) {
    if (/(?<!=)"(?!>)/.test(value)) {
        console.log(`${name}:`);
        console.log(styleText("red", `double quotes should be replaced with “” characters`));
        console.log();
    }
}

/** @type {CheckerFunction} */
function checkNoSpaceBeforeEllipsis(name, value) {
    if (/\s+…/.test(value)) {
        console.log(`${name}:`);
        console.log(styleText("red", `there should be no space before the ellipsis (…) character`));
        console.log();
    }
}
