"""Compara os campos das entidades JPA com as colunas do banco após as migrações V1 e V2.
As colunas vêm de um arquivo TSV extraído de information_schema.columns (tabela, posição, coluna, ...).
Uso: python3 comparar_entidades_banco.py <raiz-do-repositório> <columns.tsv>"""
import re, glob, os, sys, csv, collections
raiz, tsv = sys.argv[1], sys.argv[2]
base = f'{raiz}/Prototipos/backend/src/main/java/com/ditec/assistencia/entity'
tabelas = collections.OrderedDict()
for r in csv.reader(open(tsv, encoding='utf-8'), delimiter='\t'):
    tabelas.setdefault(r[0], []).append(r[2])
snake = lambda n: re.sub(r'(?<!^)(?=[A-Z])', '_', n).lower()
total_ok = total_campos = 0
for f in sorted(glob.glob(f'{base}/*.java')):
    s = open(f, encoding='utf-8').read()
    tb = re.search(r'@Table\(name\s*=\s*"(\w+)"', s).group(1)
    campos = []
    for m in re.finditer(r'((?:@\w+(?:\([^)]*\))?\s*)*)private\s+([\w<>]+)\s+(\w+)\s*(?:=[^;]*)?;', s):
        ann, tp, nm = m.groups()
        col = re.search(r'@JoinColumn\(name\s*=\s*"(\w+)"', ann) or re.search(r'@Column\([^)]*name\s*=\s*"(\w+)"', ann)
        campos.append(col.group(1) if col else snake(nm))
    so_ent = sorted(set(campos) - set(tabelas[tb])); so_tab = sorted(set(tabelas[tb]) - set(campos))
    ok = not so_ent and not so_tab; total_ok += ok; total_campos += len(campos)
    print(f"{'OK ' if ok else 'DIVERGE'} {os.path.basename(f):18} tabela={tb:15} campos={len(campos):2} colunas={len(tabelas[tb]):2} só-entidade={so_ent or '-'} só-tabela={so_tab or '-'}")
print(f"\nResumo: {total_ok}/{len(tabelas)} entidades coincidem com as tabelas (V1+V2); {total_campos} campos conferidos")
