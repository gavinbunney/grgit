package org.ajoberstar.grgit.operation

import spock.lang.Unroll

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Status
import org.ajoberstar.grgit.exception.GrgitException
import org.ajoberstar.grgit.fixtures.GitTestUtil
import org.ajoberstar.grgit.fixtures.MultiGitOpSpec
import org.ajoberstar.grgit.service.RepositoryService
import org.ajoberstar.grgit.util.JGitUtil

import static org.ajoberstar.grgit.operation.MergeOp.Mode.*

class MergeOpSpec extends MultiGitOpSpec {
	RepositoryService localGrgit
	RepositoryService remoteGrgit

	def setup() {
		remoteGrgit = init('remote')

		repoFile(remoteGrgit, '1.txt') << '1.1\n'
		remoteGrgit.add(patterns: ['.'])
		remoteGrgit.commit(message: '1.1', all: true)
		repoFile(remoteGrgit, '2.txt') << '2.1\n'
		remoteGrgit.add(patterns: ['.'])
		remoteGrgit.commit(message: '2.1', all: true)

		localGrgit = clone('local', remoteGrgit)

		remoteGrgit.checkout(branch: 'ff', createBranch: true)

		repoFile(remoteGrgit, '1.txt') << '1.2\n'
		remoteGrgit.commit(message: '1.2', all: true)
		repoFile(remoteGrgit, '1.txt') << '1.3\n'
		remoteGrgit.commit(message: '1.3', all: true)

		remoteGrgit.checkout(branch: 'clean', startPoint: 'master', createBranch: true)

		repoFile(remoteGrgit, '3.txt') << '3.1\n'
		remoteGrgit.add(patterns: ['.'])
		remoteGrgit.commit(message: '3.1', all: true)
		repoFile(remoteGrgit, '3.txt') << '3.2\n'
		remoteGrgit.commit(message: '3.2', all: true)

		remoteGrgit.checkout(branch: 'conflict', startPoint: 'master', createBranch: true)

		repoFile(remoteGrgit, '2.txt') << '2.2\n'
		remoteGrgit.commit(message: '2.2', all: true)
		repoFile(remoteGrgit, '2.txt') << '2.3\n'
		remoteGrgit.commit(message: '2.3', all: true)

		localGrgit.checkout(branch: 'merge-test', createBranch: true)

		repoFile(localGrgit, '2.txt') << '2.a\n'
		localGrgit.commit(message: '2.a', all: true)
		repoFile(localGrgit, '2.txt') << '2.b\n'
		localGrgit.commit(message: '2.b', all: true)

		localGrgit.fetch()
	}

	@Unroll('merging #head with #mode does a fast-forward merge')
	def 'fast-forward merge happens when expected'() {
		given:
		localGrgit.checkout(branch: 'master')
		when:
		localGrgit.merge(head: head, mode: mode)
		then:
		localGrgit.status().clean
		localGrgit.head() == remoteGrgit.resolveCommit(head - 'origin/')
		where:
		head        | mode
		'origin/ff' | DEFAULT
		'origin/ff' | ONLY_FF
		'origin/ff' | NO_COMMIT
	}

	@Unroll('merging #head with #mode creates a merge commit')
	def 'merge commits created when expected'() {
		given:
		def oldHead = localGrgit.head()
		def mergeHead = remoteGrgit.resolveCommit(head - 'origin/')
		when:
		localGrgit.merge(head: head, mode: mode)
		then:
		localGrgit.status().clean
		localGrgit.log {
			includes = ['HEAD']
			excludes = [oldHead.id, mergeHead.id]
		}.size() == 1
		where:
		head           | mode
		'origin/ff'    | CREATE_COMMIT
		'origin/clean' | DEFAULT
		'origin/clean' | CREATE_COMMIT
	}

	@Unroll('merging #head with #mode merges but leaves them uncommitted')
	def 'merge left uncommitted when expected'() {
		given:
		def oldHead = localGrgit.head()
		def mergeHead = remoteGrgit.resolveCommit(head - 'origin/')
		when:
		localGrgit.merge(head: head, mode: mode)
		then:
		localGrgit.status() == status
		localGrgit.log {
			includes = ['HEAD']
			excludes = [oldHead.id]
		}.size() == 0
		repoFile(localGrgit, '.git/MERGE_HEAD').text.trim() == mergeHead.id
		where:
		head           | mode      | status
		'origin/clean' | NO_COMMIT | new Status(['3.txt'] as Set, [] as Set, [] as Set, [] as Set, [] as Set, [] as Set)
	}

	@Unroll('merging #head with #mode squashes changes but leaves them uncommitted')
	def 'squash merge happens when expected'() {
		given:
		def oldHead = localGrgit.head()
		when:
		localGrgit.merge(head: head, mode: mode)
		then:
		localGrgit.status() == status
		localGrgit.log {
			includes = ['HEAD']
			excludes = [oldHead.id]
		}.size() == 0
		!repoFile(localGrgit, '.git/MERGE_HEAD').exists()
		where:
		head           | mode   | status
		'origin/ff'    | SQUASH | new Status([] as Set, ['1.txt'] as Set, [] as Set, [] as Set, [] as Set, [] as Set)
		'origin/clean' | SQUASH | new Status(['3.txt'] as Set, [] as Set, [] as Set, [] as Set, [] as Set, [] as Set)
	}

	@Unroll('merging #head with #mode fails and leaves state as before')
	def 'merge fails as expected'() {
		given:
		def oldHead = localGrgit.head()
		when:
		localGrgit.merge(head: head, mode: mode)
		then:
		localGrgit.head() == oldHead
		localGrgit.status().clean
		thrown(GrgitException)
		where:
		head              | mode
		'origin/clean'    | ONLY_FF
		'origin/conflict' | DEFAULT
		'origin/conflict' | ONLY_FF
		'origin/conflict' | CREATE_COMMIT
		'origin/conflict' | SQUASH
		'origin/conflict' | NO_COMMIT
	}
}