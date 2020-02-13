Preface: DB Setup
=================

The python standard library includes `sqlite`__::

__ https://www.sqlite.org/index.html

    >>> import sqlite3
    >>> from pprint import pprint, pformat
    >>> from __future__ import print_function
    >>> import urllib2

We have a few functions to build an audit DB::

    >>> import audit
    >>> db = audit.DB(sqlite3.connect('rev_check.db'))
    >>> db.init_tables()
    [u'genesis', u'snapshot']


RHOC Snapshot
=============

Let's load the `snapshot` table with RHOC balances as of ethereum block 9371743:

    >>> with open('src/main/resources/wallets_9371743_withzeros.txt') as w:
    ...     db.load('snapshot', w)

How many RHOC account balances do we have?

    >>> _cols, [[qty_rhoc]] = db.query('select count(*) qty from snapshot')
    >>> qty_rhoc
    18532

Recall the RHOC contract has 8 decimals:

    >>> rhoc8 = 10 ** 8

The total of RHOC balances should be 1 billion (10^9) RHOC:

    >>> _, [[total]] = db.query('select sum(bal) from snapshot')
    >>> total / rhoc8 == 10 ** 9
    True

What are the top 10?
    >>> _, top_rhoc = db.query('select addr, bal from snapshot order by bal desc limit 10'); pprint(top_rhoc)
    [(u'0x1c73d4ff97b9c8299f55d3b757b70979ee718754', 27466403837716800),
     (u'0x0000000000000000000000000000000000000000', 12933642600000000),
     (u'0xd35a2d8c651f3eba4f0a044db961b5b0ccf68a2d', 7793221780308682),
     (u'0x287550958be9d74d7f7152c911ba0b71801153a8', 3117693198495265),
     (u'0x689c56aef474df92d44a1b70850f808488f9769c', 2882664288573629),
     (u'0x899b5d52671830f567bf43a14684eb14e1f945fe', 2878776400000000),
     (u'0x62917a5bce92bc34bdc6b9254b3cc426d52752f3', 2108874000000000),
     (u'0x583c3bceb7b517acaeca84bce7c7266d7290a7aa', 1483867335645073),
     (u'0xf15230cba5b211b7cb6a4ae7cfc5a84e9cb6865d', 1420881000000000),
     (u'0xbee7cce5b6e2eb556219eef8f3061aa9ff0630e9', 1260711500000000)]


Genesis REV Wallets Proposal
============================

    >>> genesis_addr = 'https://raw.githubusercontent.com/rchain/rchain/dev/wallets.txt'
    >>> db.load('genesis', urllib2.urlopen(genesis_addr))

How many REV wallets do we have?

    >>> _cols, [[qty_rev]] = db.query('select count(*) qty from genesis')
    >>> qty_rev
    18562

How does the number of REV wallets compare to the number of RHOC wallets?

    >>> qty_rhoc - qty_rev
    0

What are the top 10?
    >>> _, top_rev = db.query('select addr, bal from snapshot order by bal desc limit 10')
    >>> top_rhoc == top_rev
    True

    >>> db.sql('''
    ... create view adj as
    ... select s.addr, s.bal bal_rhoc, g.bal bal_rev, g.bal - s.bal as delta
    ... from snapshot s
    ... join genesis g on g.addr = s.addr
    ... order by 4 desc, 1
    ... limit 10
    ... ''');

    >>> hd, data = db.query('select * from adj limit 10')
    >>> audit.show('{0:<44} {1:>10} {2:>10} {3:>10}', hd, data)
