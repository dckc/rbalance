from __future__ import print_function
from decimal import Decimal as D
import csv


class DB(object):
    schema = [
        '''
        create table snapshot (
          addr text, bal int, contract int,
          primary key(addr)
        )''',
        '''
        create table taint (
          addr text, bal int, contract int,
          primary key(addr)
        )
        ''',
        '''
        create table genesis (
          addr text, bal int, contract int,
          primary key(addr)
        )''',
    ]

    def __init__(self, conn):
        self.__db = conn

    def init_tables(self):
        cur = self.__db.cursor()
        for stmt in self.schema:
            cur.execute(stmt)
        cur.execute('''
        select name from sqlite_master
        where type='table'
        order by name
        ''')
        return [name for [name] in cur.fetchall()]

    def load(self, table, lines):
        cur = self.__db.cursor()
        stmt = 'insert into %s (addr, bal, contract) values (?, ?, ?)' % table
        for row in csv.reader(lines):
            row[0] = row[0].lower()  # normalize case of addresses
            cur.execute(stmt, row)
        self.__db.commit()

    def sql(self, sql,
            params=None):
        cur = self.__db.cursor()
        if params is not None:
            cur.execute(sql, params)
        else:
            cur.execute(sql)

    def query(self, sql):
        cur = self.__db.cursor()
        cur.execute(sql)
        return [d[0] for d in cur.description], cur.fetchall()


def show(fmt, hd, data,
         labels=1,
         decimals=0):
    q = D(10) ** -decimals
    for ix, row in enumerate(data):
        if ix == 0:
            print(fmt.format(*hd))
        txt = list(l or '' for l in row[:labels])
        nums = ['' if n is None else (D(n) / 10 ** decimals).quantize(q)
                for n in row[labels:]]
        print(fmt.format(*(txt + nums)))


def mdtable(lines):
    for line in lines:
        if not line.strip().startswith('|'):
            continue
        if '----' in line:
            continue
        row = line.split('|')[1:-1]
        yield [cell.strip() for cell in row]
