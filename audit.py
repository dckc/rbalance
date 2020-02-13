from __future__ import print_function
import csv

rhoc8 = 10 ** 8


class DB(object):
    schema = [
        '''
        create table snapshot (
          addr text, bal int, contract int,
          primary key(addr)
        )''',
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

    def sql(self, sql):
        cur = self.__db.cursor()
        cur.execute(sql)

    def query(self, sql):
        cur = self.__db.cursor()
        cur.execute(sql)
        return [d[0] for d in cur.description], cur.fetchall()


def show(fmt, hd, data):
    for ix, (addr, rhoc, rev, delta) in enumerate(data):
        if ix == 0:
            print(fmt.format(*hd))
        print(fmt.format(addr, rhoc / rhoc8, rev / rhoc8, delta / rhoc8))
