import Levenshtein as lev
import os
import platform
import spacy_stanza
import sys

from collections import Counter
from fastapi import FastAPI
from nltk.stem.snowball import SnowballStemmer
from pydantic import BaseModel

if platform.system() == "Windows":
    system_drive = os.getenv("SYSTEMDRIVE")

    if not system_drive.endswith('\\'):
        system_drive += '\\'

    directory = system_drive + "srv"
else:
    directory = "/srv"

directory = os.path.join(directory, 'SignLanguageInterpreter.API', 'landmark')

app = FastAPI()
nlp = spacy_stanza.load_pipeline("ro")
stemmer = SnowballStemmer("romanian")

vocabulary = [file_name[:file_name.find('__fps')] for file_name in os.listdir(directory)]


def cosine_dist(a, b):
    a_vals = Counter(a)
    b_vals = Counter(b)

    chars = list(a_vals.keys() | b_vals.keys())
    a_vect = [a_vals.get(c, 0) for c in chars]
    b_vect = [b_vals.get(c, 0) for c in chars]

    len_a = sum(av * av for av in a_vect) ** 0.5
    len_b = sum(bv * bv for bv in b_vect) ** 0.5
    dot = sum(av * bv for av, bv in zip(a_vect, b_vect))
    cosine = dot / (len_a * len_b)

    return 1 - cosine


def find_closest_words(doc, is_end):
    doc_len = len(doc)

    def search_in_vocab(word):
        distance_map = dict()

        for vocab_word in vocabulary:
            max_len = max(1, len(word) // 2)
            start_a = word[:max_len]
            start_b = vocab_word[:max_len]

            if start_a == start_b:
                dist = lev.distance(vocab_word, word)
            else:
                dist = sys.maxsize
                
            distance_map[vocab_word] = dist
        
        min_dist = min(distance_map.values())
        
        candidates = [key for key, value in distance_map.items() if value == min_dist]
        candidates = sorted(candidates, key=lambda x: cosine_dist(word, x))

        for candidate in candidates:
            if min_dist < int(len(candidate) / 2):
                return candidate
        
        return None
    

    def process_token(token, idx):
        token_text = token.text.lower()
        token_lemma = token.lemma_.lower()
        token_stem = stemmer.stem(token.text).lower()

        # weirdness
        if token_text == 'm':
            if idx < doc_len - 1 and doc[idx + 1].pos_ == 'AUX':
                return ['eu']
            else:
                return ['m']

        elif token_text == 'mânc' or token_text == 'mănânc':
            return ['mânca']
            
        elif token_text == 'n':
            if idx < doc_len - 1 and (doc[idx + 1].pos_ == 'ADV' or doc[idx + 1].pos_ == 'NOUN'):
                return ['în']
            elif idx >= 1 and (doc[idx - 1].pos_ == 'PRON' or doc[idx - 1].pos_ == 'VERB'):
                return ['în']
            else:
                return ['n']
            
        elif token_text == 'ă':
            return ['ă']

        # connection words
        elif token_text == 'dar':
            if 'CONJ' in token.pos_:
                return ['dar (conjuncție)']
            else:
                return ['dar (cadou)']
            
        elif token_text == 'ori':
            if 'CONJ' in token.pos_:
                return ['sau']
            else:
                return ['ori']
            
        elif token_text == 'fie':
            if 'CONJ' in token.pos_:
                return ['sau']
            else:
                return ['fi']
            
        elif token_text == 'că':
            return []
            
        elif token_text == 'ci':
            return []
            
        elif token_text == 'de':
            if idx < doc_len - 1 and doc[idx + 1].text == 'ce':
                return []
            else:
                return ['de']
            
        elif token_text == 'ce':
            if idx >= 1 and doc[idx - 1].text == 'de':
                return ['de ce']
            else:
                return ['ce']
            
        elif token_text == 'la':
            if idx < doc_len - 1 and doc[idx + 1].text == 'revedere':
                return []
            else:
                return ['la']
            
        elif token_text == 'revedere':
            if idx >= 1 and doc[idx - 1].text == 'la':
                return ['la revedere']
            else:
                return ['vedea']
            
        elif token_text == 'deși':
            return ['chiar', 'dacă']
        
        elif token_text == 'da':
            if token.pos_ == 'ADV':
                return ['da (adverb)']
            else:
                return ['da (verb)']
            
        elif (token.pos_ == 'AUX' or token.pos_ == 'PART') and idx < doc_len - 1 and (doc[idx + 1].pos_ == 'VERB' or doc[idx + 1].pos_ == 'AUX' or doc[idx + 1].pos_ == 'PART'):
            return []
            
        elif token.pos_ == 'DET' and idx < doc_len - 1 and doc[idx + 1].pos_ == 'NUM':
            return []
        
        elif token_text == 'mai' and idx < doc_len - 1 and (doc[idx + 1].text.lower() == 'un' or doc[idx + 1].text.lower() == 'o'):
            return ['încă']

        # adjectives, nouns, verbs
        elif token_lemma == 'da':
            return ['da (verb)']
        
        elif token.pos_ == 'VERB':
            word = search_in_vocab(token_lemma)
            if word:
                return [word]
            word = search_in_vocab(token_stem)
            if word:
                return [word]
            else:
                return [x for x in token_lemma]
        
        elif token.pos_ == 'ADJ' or token.pos_ == 'NOUN':
            word = search_in_vocab(token_lemma)
            if word:
                return [word]
            word = search_in_vocab(token_text)
            if word:
                return [word]
            word = search_in_vocab(token_stem)
            if word:
                return [word]
            else:
                return [x for x in token_lemma]
            
        # proper name
        elif token.pos_ == 'PROPN':
            return [x for x in token_text]
            
        # numeral
        elif token.pos_ == 'NUM' or (token.pos_ == 'X' and any(char.isdigit() for char in token_text)):
            # only digits
            if '.' in token_text:
                token_text = token_text.replace('.', '')
                if ',' in token_text:
                    split = token_text.split(',')
                    res = []
                    res.extend([x for x in split[0]])
                    res.append('virgulă')
                    res.extend([x for x in split[1]])
                    return res

            # from 1M up, there are digits and letter
            # (e.g. un milion, 100 (de) milioane)
            word = search_in_vocab(token_text)
            if word:
                return [word]
            word = search_in_vocab(token_lemma)
            if word:
                return [word]
            else:
                return [x for x in token_text]
            
        # pronoun:
        elif token.pos_ == "PRON":
            if token_text == 'eu' or token_text == 'mine' or token_text == 'mă' or token_text == 'mie' or token_text == 'îmi' or token_text == 'mi':
                return ['eu']
            elif token_text == 'tu' or token_text == 'tine' or token_text == 'te' or token_text == 'ție' or token_text == 'îți' or token_text == 'ți':
                return ['tu']
            elif token_text == 'el' or token_text == 'îl' or token_text == 'l' or token_text == 'lui' or token_text == 'îi' or token_text == 'i':
                return ['el']
            elif token_text == 'ea' or token_text == 'o':
                return ['ea']
            elif token_text == 'noi' or token_text == 'ne' or token_text == 'nouă' or token_text == 'ni':
                return ['noi']
            elif token_text == 'voi' or token_text == 'vă' or token_text == 'vouă' or token_text == 'vi':
                return ['voi']
            elif token_text == 'ei' or token_text == 'îi' or token_text == 'i' or token_text == 'lor' or token_text == 'le' or token_text == 'li':
                return ['ei']
            elif token_text == 'ele' or token_text == 'le':
                return ['ele']
            elif token_text == 'unul' or token_text == 'una':
                return ['un']
            else:
                word = search_in_vocab(token_lemma)
                if word:
                    return [word]
                else:
                    return []
            
        # determiner:
        elif token.pos_ == "DET":
            if token_text == 'un' or token_text == 'o':
                return ['un']
            elif token_text == 'meu' or token_text == 'mea' or token_text == 'mei' or token_text == 'mele':
                return ['meu']
            elif token_text == 'tău' or token_text == 'ta':
                return ['tău']
            elif token_text == 'lui':
                return ['el']
            elif token_text == 'ei':
                return ['ea']
            elif token_text == 'său' or token_text == 'sa':
                return ['său']
            elif token_text == 'nostru':
                return ['nostru']
            elif token_text == 'vostru':
                return ['vostru']
            elif token_text == 'lor':
                return ['ei']
            else:
                word = search_in_vocab(token_lemma)
                if word:
                    return [word]
                else:
                    return []

        else:
            word = search_in_vocab(token_lemma)
            if word:
                return [word]
            else:
                return []

    if is_end:
        res_0 = process_token(doc[-3], doc_len - 3)
        res_1 = process_token(doc[-2], doc_len - 2)
        res_2 = process_token(doc[-1], doc_len - 1)

        return [res_0, res_1, res_2]
    else:
        res_0 = process_token(doc[-3], doc_len - 3)
        
        return [res_0]


def sentence_to_lexemes(sentence, is_end):
    doc = nlp(sentence)
    doc_len = len(doc)

    while doc_len < 3:
        if is_end:
            sentence += " ."
            doc = nlp(sentence)
            doc_len = len(doc)
        else:
            raise ValueError('Send at least 3 tokens')

    return find_closest_words(doc, is_end)


class SentencePayload(BaseModel):
    sentence: str
    is_end: bool


@app.post("/sentence/")
async def process_text(payload: SentencePayload):
    # return sentence_to_lexemes(payload.sentence, payload.is_end)

    print(f'sentence: {payload.sentence}\nis_end: {payload.is_end}\n')
    lexemes = sentence_to_lexemes(payload.sentence, payload.is_end)
    print(f'lexemes: {lexemes}')

    return lexemes
